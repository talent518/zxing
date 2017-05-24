package com.google.zxing.client.android.transfer;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * 文件传输的头信息
 * 
 * @author abao<talent518@yeah.net>
 */
@XStreamAlias("tf")
public class Transfer {
	@XStreamAlias("fc")
	public String fileCode = "";

	@XStreamAlias("fn")
	public String fileName = "";

	@XStreamAlias("fh")
	public String fileHash = "";

	@XStreamAlias("fs")
	public int fileSize = 0;

	@XStreamAlias("bc")
	public int blockCount = 0;

	@XStreamAlias("bs")
	public int blockSize = 0;

	@XStreamAlias("cbc")
	public int completeBlockCount = 0;
	
	@XStreamAlias("ct")
	public long createDateline = 0;
	
	@XStreamAlias("mt")
	public long modifyDateline = 0;
}
