package com.google.zxing.client.android;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.zip.GZIPInputStream;

import com.google.zxing.Result;
import com.google.zxing.client.android.transfer.Block;
import com.google.zxing.client.android.transfer.ResultTransfer;
import com.google.zxing.client.android.transfer.Transfer;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

import android.content.res.Resources;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;

/**
 * 文件传输(QRCODE 二维码)
 * 
 * @author abao<talent518@yeah.net>
 */
public class FileTransfer {
	private static final String TAG = FileTransfer.class.getSimpleName();
	private final File basePath = new File(Environment.getExternalStorageDirectory(), "BarcodeScanner");
	private final File transferPath = new File(basePath, "Transfer");

	private final XStream xStream = new XStream(new DomDriver());

	private final CaptureActivity activity;
	private Transfer fileTransfer;

	public FileTransfer(CaptureActivity activity) {
		this.activity = activity;
		if (!transferPath.exists() && !transferPath.mkdirs()) {
			Log.w(TAG, "Couldn't make dir " + transferPath);
		}

		xStream.autodetectAnnotations(true);
		xStream.toXML(new Transfer());
		xStream.toXML(new Block());
	}

	public ResultTransfer saveToTarget(Result result) {
		String xml = result.getText();
		Resources res = activity.getResources();
		Object object;
		File xmlFile, dataFile;
		FileWriter writer;
		ResultTransfer resultTransfer;

		try {
			object = xStream.fromXML(xml);
		} catch (Exception exception) {
			exception.printStackTrace();
			return null;
		}

		resultTransfer = new ResultTransfer();
		resultTransfer.rawResult = result;
		resultTransfer.status = false;

		if (object instanceof Transfer) {
			Transfer transfer = (Transfer) object;
			xmlFile = new File(transferPath, transfer.fileCode + ".xml");
			dataFile = new File(transferPath, transfer.fileName);

			if (dataFile.exists()) {
				resultTransfer.message = String.format(res.getString(R.string.transfer_existed), dataFile.getAbsoluteFile());
			} else if (xmlFile.exists()) {
				resultTransfer.message = String.format(res.getString(R.string.transfer_existed), xmlFile.getAbsoluteFile());
			} else {
				try {
					writer = new FileWriter(xmlFile);
					writer.write(xml);
					writer.close();
					fileTransfer = transfer;

					resultTransfer.status = true;
					resultTransfer.message = String.format(res.getString(R.string.transfer_begin), xmlFile.getAbsoluteFile(), transfer.fileSize, transfer.blockCount, transfer.blockSize);
				} catch (IOException e) {
					e.printStackTrace();

					resultTransfer.message = String.format(res.getString(R.string.transfer_no_write), xmlFile.getAbsoluteFile());
				}

			}
		} else if (object instanceof Block) {
			Block block = (Block) object;

			xmlFile = new File(transferPath, block.fileCode + ".xml");

			if (fileTransfer == null || !fileTransfer.fileHash.equals(block.fileCode)) {
				if (xmlFile.exists()) {
					try {
						fileTransfer = (Transfer) xStream.fromXML(xmlFile);
					} catch (Exception e) {
						e.printStackTrace();

						resultTransfer.message = String.format(res.getString(R.string.transfer_no_write), xmlFile.getAbsoluteFile());
						return resultTransfer;
					}
				} else {
					resultTransfer.message = res.getString(R.string.transfer_no_file);
					return resultTransfer;
				}
			}

			dataFile = new File(transferPath, fileTransfer.fileName);

			byte[] bytes = Base64.decode(block.blockData, Base64.DEFAULT);
			bytes = unGZip(bytes);

			if (bytes == null) {
				resultTransfer.message = res.getString(R.string.transfer_decode_error);
				return resultTransfer;
			}

			if (fileTransfer.blockCount == fileTransfer.completeBlockCount) {
				resultTransfer.status = true;
				resultTransfer.message = complete(dataFile);
			} else if (block.blockSeek == fileTransfer.completeBlockCount + 1 && block.blockSeek <= fileTransfer.blockCount) {
				try {
					if (!dataFile.exists()) {
						dataFile.createNewFile();
						fileTransfer.createDateline = System.currentTimeMillis();
					}
					FileOutputStream output = new FileOutputStream(dataFile, true);
					output.write(bytes);
					output.close();

					fileTransfer.completeBlockCount++;
					fileTransfer.modifyDateline = System.currentTimeMillis();

					try {
						writer = new FileWriter(xmlFile);
						writer.write(xStream.toXML(fileTransfer));
						writer.close();
					} catch (IOException e) {
					}
					resultTransfer.status = true;
					resultTransfer.message = complete(dataFile);
				} catch (IOException e) {
					e.printStackTrace();

					resultTransfer.message = String.format(res.getString(R.string.transfer_no_write), dataFile.getAbsoluteFile());
				}
			} else {
				resultTransfer.message = String.format(res.getString(R.string.transfer_seek_error), dataFile.getAbsoluteFile(), fileTransfer.completeBlockCount + 1);
			}
		} else {
			resultTransfer.message = xml;
		}
		return resultTransfer;
	}

	/**
	 * 传输完成消息
	 * 
	 * @param activity
	 * @param dataFile
	 */
	public String complete(File dataFile) {
		Resources res = activity.getResources();

		DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String createTime = format.format(fileTransfer.createDateline);
		String modifyTime = format.format(fileTransfer.modifyDateline);
		long useSecond = (fileTransfer.modifyDateline - fileTransfer.createDateline) / 1000;
		if (fileTransfer.blockCount == fileTransfer.completeBlockCount) {
			if (fileTransfer.fileHash.equalsIgnoreCase(hashFile(dataFile, "MD5"))) {
				return String.format(res.getString(R.string.transfer_complete, dataFile.getAbsoluteFile(), createTime, modifyTime, useSecond), dataFile.getAbsoluteFile());
			} else {
				return String.format(res.getString(R.string.transfer_hash, dataFile.getAbsoluteFile(), fileTransfer.fileHash, createTime, modifyTime, useSecond), dataFile.getAbsoluteFile());
			}
		} else {
			return String.format(res.getString(R.string.transfer_progress), dataFile.getAbsoluteFile(), fileTransfer.fileSize, fileTransfer.completeBlockCount, fileTransfer.blockCount, createTime, modifyTime, useSecond);
		}
	}

	/**
	 * gzip解码
	 * 
	 * @param bContent
	 * @return 原始数据
	 */
	public static byte[] unGZip(byte[] bContent) {

		byte[] data;
		try {
			// gzip 解压缩
			ByteArrayInputStream bais = new ByteArrayInputStream(bContent);
			GZIPInputStream gzip = new GZIPInputStream(bais);

			byte[] buf = new byte[1024];
			int len = 0;

			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			while ((len = gzip.read(buf, 0, 1024)) != -1) {
				baos.write(buf, 0, len);
			}
			gzip.close();
			baos.flush();

			data = baos.toByteArray();

			baos.close();

			return data;

		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * 生成文件Hash值，适用于上G大的文件
	 */
	public static String hashFile(File file, String algo) {
		if (!file.exists() || !file.isFile()) {
			return null;
		}
		MessageDigest digest = null;
		FileInputStream in = null;
		byte buffer[] = new byte[1024];
		int len;
		try {
			digest = MessageDigest.getInstance(algo);
			in = new FileInputStream(file);
			while ((len = in.read(buffer, 0, 1024)) > 0) {
				digest.update(buffer, 0, len);
			}
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		BigInteger bigInt = new BigInteger(1, digest.digest());
		return bigInt.toString(digest.getDigestLength());
	}
}
