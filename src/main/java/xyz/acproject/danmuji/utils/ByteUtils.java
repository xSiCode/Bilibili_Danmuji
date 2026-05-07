package xyz.acproject.danmuji.utils;


import org.apache.commons.compress.compressors.brotli.BrotliCompressorInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.InflaterOutputStream;

/**
 * @ClassName ByteUtils
 * @Description TODO
 * @author BanqiJane
 * @date 2020年8月10日 下午12:31:34
 *
 * @Copyright:2020 blogs.acproject.xyz Inc. All rights reserved.
 */
public class ByteUtils {
	public static final int UNICODE_LEN = 2;

	/**
	 * 将两个byte数组拼接为单独对象
	 * 
	 * @param byte_1  待拼接byte数组1
	 * @param byte_2  待拼接byte数组1
	 * @return 返回拼接后的byte数组
	 */
	public static byte[] byteMerger(byte[] byte_1, byte[] byte_2) {
		byte[] byte_3 = new byte[byte_1.length + byte_2.length];
		System.arraycopy(byte_1, 0, byte_3, 0, byte_1.length);
		System.arraycopy(byte_2, 0, byte_3, byte_1.length, byte_2.length);
		return byte_3;
	}

	/**
	 * 将bytebuffer转为byte数组
	 * 
	 * @param bytes 待转bytebuffer
	 * @return 转换后的byte数组
	 */
	public static byte[] decodeValue(ByteBuffer bytes) {
		int len = bytes.limit() - bytes.position();
		byte[] bytes1 = new byte[len];
		bytes.get(bytes1);
		return bytes1;
	}


	//使用BrotliCompressorInputStream解压brotli
	public static byte[] BytesToBrotliInflate(byte[] bs){
		byte[] b = null;
		try (BrotliCompressorInputStream brotliCompressorInputStream = new BrotliCompressorInputStream(new ByteArrayInputStream(bs))){
			final ByteArrayOutputStream bos = new ByteArrayOutputStream();
			int readByte = -1;
			while ((readByte = brotliCompressorInputStream.read()) != -1) {
				bos.write(readByte);
			}
			b = bos.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return b;
	}
	/**
	 * byte[]的zlib解压
	 * 
	 * @param bs 待解压byte数组
	 * @return b 解压完成的byte[]
	 */
	public static byte[] BytesTozlibInflate(byte[] bs) {
		byte[] b = null;
		ByteArrayOutputStream bos = null;
		InflaterOutputStream zos = null;
		try {
			bos = new ByteArrayOutputStream();
			zos = new InflaterOutputStream(bos);
			zos.write(bs);
			zos.close();
			b = bos.toByteArray();
			return b;
		} catch (Exception ex) {
			ex.printStackTrace();
		}finally {
			if(bos!=null){
				try {
					bos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return b;
    }

	/**
	 * 截取byte数组的一部分
	 * 
	 * @param bytes 待截取的byte数组
	 * @param begin 开始截取的位置
	 * @param count 截取的字节
	 * @return 截取后的byte数组
	 */
	public static byte[] subBytes(byte[] bytes,int begin,int count) {
//		byte[] bs = new byte[count];
//		for(int i =begin;i<begin+count;i++) bs[i-begin] =bytes[i];
		byte[] bs = new byte[count];
		System.arraycopy(bytes, begin, bs, 0, count);
		return bs;
	}

	/**
     * 转换字符数组为定长byte[]
     * @param chars              字符数组
     * @return 若指定的定长不足返回null, 否则返回byte数组
     */
    public static byte[] Chars2Bytes_LE(char[] chars){
        if(chars == null)
            return null;
 
        int iCharCount = chars.length;       
        byte[] rst = new byte[iCharCount*UNICODE_LEN];
        int i = 0;
        for( i = 0; i < iCharCount; i++){
            rst[i*2] = (byte)(chars[i] & 0xFF);
            rst[i*2 + 1] = (byte)(( chars[i] & 0xFF00 ) >> 8);
        }   
 
        return rst;
    }

	/**
	 * byte[] 转long
	 * 
	 * @param bs
	 * @return
	 * @throws Exception
	 */
	public static long byteslong(byte[] bs)  throws Exception {
        int bytes = bs.length;
        if(bytes > 1) {
        if((bytes % 2) != 0 || bytes > 8) {
            throw new Exception("not support");
        }}
        switch(bytes) {
        case 0:
            return 0;
        case 1:
            return (long)((bs[0] & 0xff));
        case 2:
            return (long)((bs[0] & 0xff) <<8 | (bs[1] & 0xff));
        case 4:
            return (long)((bs[0] & 0xffL) <<24 | (bs[1] & 0xffL) << 16 | (bs[2] & 0xffL) <<8 | (bs[3] & 0xffL));
        case 8:
            return (long)((bs[0] & 0xffL) <<56 | (bs[1] & 0xffL) << 48 | (bs[2] & 0xffL) <<40 | (bs[3] & 0xffL)<<32 | 
                    (bs[4] & 0xffL) <<24 | (bs[5] & 0xffL) << 16 | (bs[6] & 0xffL) <<8 | (bs[7] & 0xffL));
        default:
            throw new Exception("not support");     
        }
        //return 0;
    }

}
