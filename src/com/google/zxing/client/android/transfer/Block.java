package com.google.zxing.client.android.transfer;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * 文件传输的数据块
 * 
 * @author abao<talent518@yeah.net>
 */
@XStreamAlias("bk")
public class Block {
	@XStreamAlias("fc")
	public String fileCode = "";

	@XStreamAlias("bd")
	public String blockData = "";

	@XStreamAlias("bs")
	public int blockSeek = 0;
}