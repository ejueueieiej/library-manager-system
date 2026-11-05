package org.libaray_manager_system.utils;

import org.libaray_manager_system.exception.BusinessException;

import java.nio.ByteBuffer;
import java.nio.charset.*;

public class TextDecodeUtils {
    /** 解析结果：正文 + 最终用的编码名 */
    public record Decoded(String text, String charset) {}

    public static Decoded decode(byte[] bytes) {
        if (bytes == null||bytes.length==0) {
            throw new BusinessException("文件为空");
        }
        /*
        * 1.BOM识别编码方式
        * Byte在java里默认是有符号整数，而0xEF默认为int类型，不能直接用等号来判断bytes[0]==0xEF
        * */
        if(bytes.length>=3 && (bytes[0]&0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF){
            return new Decoded(new String(bytes,3,bytes.length-3, StandardCharsets.UTF_8),"utf-8");
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new Decoded(new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE), "UTF-16LE");
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new Decoded(new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE), "UTF-16BE");
        }
        /*
        * 2.严格按照UTF-8进行编码
        * */
        String utf_8 = tryDecode(bytes,StandardCharsets.UTF_8);
        if(utf_8!=null){
            return new Decoded(utf_8,"utf-8");
        }

        String gb = tryDecode(bytes,Charset.forName("GB18030"));
        if(gb!=null){
            return new Decoded(gb,"gb18030");
        }
        throw new BusinessException("无法识别文件编码，请转为UTF-8或者GBK格式进行上传");
    }
    //解码方法
    private static String tryDecode(byte[] bytes, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder()
                //格式非法检测：必须是 REPORT —— 遇到非法字节直接抛异常，
                //这样"能解出来"才等于"真的是这种编码"。用 REPLACE 会悄悄换成 �，
                //于是 GBK 文件也能"被 UTF-8 解码成功"，下面 GB18030 那段永远走不到。
                .onMalformedInput(CodingErrorAction.REPORT)
                //合法，但无对应字符检测
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try{
            //decoder只能对ByteBuffer进行解码，所以要用BytrBuffer.wrap()转一下
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        }
        catch (CharacterCodingException e){
            return null;
        }
    }
}
