package com.recmod;

import java.io.ByteArrayOutputStream;

/**
 * JCodec が出す IDR スライスは、idr_pic_id がいつも 0。規格では、続けて来る IDR 同士で、この値を変えなければならない
 * (同じだと、1 枚の絵の続きとみなされる)ので、スライスヘッダーの idr_pic_id を 1 に書き換える。
 * スライスヘッダーの並びは、JCodec の出力が決まった形なので、その形でなければ、何も変えない。
 *   first_mb_in_slice(ue)=0 → '1' / slice_type(ue)=2 → '011' / pps_id(ue)=0 → '1' / frame_num(4bit)=0 / idr_pic_id(ue)=0 → '1'
 */
final class H264Fix {

	private static final String HEAD = "1" + "011" + "1" + "0000" + "1";

	private H264Fix() {
	}

	/** nal は、NAL ヘッダー(1 バイト)から始まる、エスケープ済みの NAL。 */
	static byte[] withIdrPicId1(byte[] nal) {
		// エスケープ(00 00 03)を外す
		ByteArrayOutputStream raw = new ByteArrayOutputStream(nal.length);
		int zeros = 0;
		for (int i = 1; i < nal.length; i++) {
			int b = nal[i] & 0xFF;
			if (zeros >= 2 && b == 3) {
				zeros = 0;
				continue;
			}
			raw.write(b);
			zeros = b == 0 ? zeros + 1 : 0;
		}
		byte[] rbsp = raw.toByteArray();
		int bits = rbsp.length * 8;
		// 最後の 1 のビット(rbsp_stop_one_bit)の位置
		int stop = bits - 1;
		while (stop >= 0 && bit(rbsp, stop) == 0) {
			stop--;
		}
		if (stop < HEAD.length()) {
			return nal;
		}
		for (int i = 0; i < HEAD.length(); i++) {
			if (bit(rbsp, i) != HEAD.charAt(i) - '0') {
				return nal;
			}
		}
		// ヘッダーの最後のビット(idr_pic_id = '1')を、1 を表す '010' に置き換える
		BitOut out = new BitOut();
		for (int i = 0; i < HEAD.length() - 1; i++) {
			out.put(bit(rbsp, i));
		}
		out.put(0);
		out.put(1);
		out.put(0);
		for (int i = HEAD.length(); i < stop; i++) {
			out.put(bit(rbsp, i));
		}
		out.put(1);                      // rbsp_stop_one_bit
		byte[] fixed = out.bytes();

		// エスケープを付け直す
		ByteArrayOutputStream result = new ByteArrayOutputStream(fixed.length + 16);
		result.write(nal[0]);
		zeros = 0;
		for (byte value : fixed) {
			int b = value & 0xFF;
			if (zeros >= 2 && b <= 3) {
				result.write(3);
				zeros = 0;
			}
			result.write(b);
			zeros = b == 0 ? zeros + 1 : 0;
		}
		return result.toByteArray();
	}

	private static int bit(byte[] data, int index) {
		return (data[index >> 3] >> (7 - (index & 7))) & 1;
	}

	private static final class BitOut {
		private final ByteArrayOutputStream out = new ByteArrayOutputStream();
		private int current;
		private int count;

		void put(int bit) {
			current = (current << 1) | bit;
			if (++count == 8) {
				out.write(current);
				current = 0;
				count = 0;
			}
		}

		byte[] bytes() {
			while (count != 0) {
				put(0);
			}
			return out.toByteArray();
		}
	}
}
