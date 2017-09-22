import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Stack;

import com.nothome.delta.Checksum;
import com.nothome.delta.DeltaException;
import com.nothome.delta.DiffWriter;
import com.nothome.delta.PatchException;

public class BP2PBDelta {

	public final static int S = Checksum.S;
	public final static boolean debug = false;
	public final static int buff_size = 64 * S;

	static public void computeDelta(InputStream source, int sourceLength, InputStream targetIS, int targetLength, DiffWriter output) throws IOException, DeltaException {
		// int sourceLength = (int) source.length(); TODO
		Checksum checksum = new Checksum();
		checksum.generateChecksums(source, sourceLength);
		source.reset();

		PushbackInputStream target = new PushbackInputStream(new BufferedInputStream(targetIS), buff_size);

		boolean done = false;
		byte buf[] = new byte[S];
		long hashf = 0;
		byte b[] = new byte[1];
		byte sourcebyte[] = new byte[S];

		if (targetLength <= S || sourceLength <= S) {
			// simply return the complete target as diff
			int readBytes;
			while ((readBytes = target.read(buf)) >= 0) {
				for (int i = 0; i < readBytes; i++) {
					output.addData(buf[i]);
				}
			}
			return;
		}

		// initialize first complete checksum.
		int bytesRead = target.read(buf, 0, S);
		int targetidx = bytesRead;

		hashf = Checksum.queryChecksum(buf, S);

		// The check for alternative hashf is only because I wanted to verify
		// that the
		// update method really is correct. I will remove it shortly.
		long alternativehashf = hashf;

		if (debug)
			System.out.println("my hashf: " + hashf + ", adler32: " + alternativehashf);

		/* This flag indicates that we've run out of source bytes */
		boolean sourceOutofBytes = false;

		while (!done) {

			int index = checksum.findChecksumIndex(hashf);
			if (index != -1) {

				boolean match = true;
				int offset = index * S;
				int length = S - 1;
				source.reset();
				source.skip(offset);
				// source.seek(offset);TODO

				// possible match, need to check byte for byte
				if (sourceOutofBytes == false && source.read(sourcebyte, 0, S) != -1) {
					for (int ix = 0; ix < S; ix++) {
						if (sourcebyte[ix] != buf[ix]) {
							match = false;
						}
					}
				} else {
					sourceOutofBytes = true;
				}

				if (match & sourceOutofBytes == false) {
					// System.out.println("before targetidx : " + targetidx );
					// The length of the match is determined by comparing bytes.
					long start = System.currentTimeMillis();

					boolean ok = true;
					byte[] sourceBuff = new byte[buff_size];
					byte[] targetBuff = new byte[buff_size];
					int source_idx = 0;
					int target_idx = 0;
					int tCount = 0;

					do {
						source_idx = source.read(sourceBuff, 0, buff_size);
						// System.out.print("Source: "+ source_idx);
						if (source_idx == -1) {
							/*
							 * Ran our of source bytes during match, so flag
							 * this
							 */
							sourceOutofBytes = true;
							// System.out.println("Source out ... target has: "
							// + target.available());
							break;
						}

						/*
						 * Don't read more target bytes then source bytes ...
						 * this is *VERY* important
						 */
						target_idx = target.read(targetBuff, 0, source_idx);
						// System.out.println(" Target: "+target_idx);
						if (target_idx == -1) {
							/*
							 * Ran out of target bytes during this match, so
							 * we're done
							 */
							// System.err.println("Ran outta bytes Sourceidx="+source_idx
							// +" targetidx:"+target_idx );
							break;
						}

						int read_idx = Math.min(source_idx, target_idx);
						int i = 0;
						do {
							targetidx++;
							++length;
							ok = sourceBuff[i] == targetBuff[i];
							i++;
							if (!ok) {
								b[0] = targetBuff[i - 1];

								if (target_idx != -1) {
									target.unread(targetBuff, i, target_idx - i);
								}
							}
						} while (i < read_idx && ok);
						b[0] = targetBuff[i - 1]; // gls100603a (fix from Dan
													// Morrione)
					} while (ok && targetLength - targetidx > 0);
					output.addCopy(offset, length);
					if (targetLength - targetidx <= S - 1) {
						buf[0] = b[0]; // don't loose this byte
						int remaining = targetLength - targetidx;
						int readStatus = target.read(buf, 1, remaining);
						targetidx += remaining;
						for (int ix = 0; ix < (remaining + 1); ix++)
							output.addData(buf[ix]);
						done = true;
					} else {
						buf[0] = b[0];
						target.read(buf, 1, S - 1);
						targetidx += S - 1;
						alternativehashf = hashf = checksum.queryChecksum(buf, S);
					}
					continue; // continue loop
				}
			}

			if (targetLength - targetidx > 0) {
				// update the adler fingerpring with a single byte

				target.read(b, 0, 1);
				targetidx += 1;

				// insert instruction with the old byte we no longer use...
				output.addData(buf[0]);

				alternativehashf = checksum.incrementChecksum(alternativehashf, buf[0], b[0]);

				for (int j = 0; j < 15; j++)
					buf[j] = buf[j + 1];
				buf[15] = b[0];
				hashf = checksum.queryChecksum(buf, S);

				if (debug)
					System.out.println("raw: " + Integer.toHexString((int) hashf) + ", incremental: " + Integer.toHexString((int) alternativehashf));

			} else {
				for (int ix = 0; ix < S; ix++)
					output.addData(buf[ix]);
				done = true;
			}

		}
	}

	public static void diff(DataOutputStream output, InputStream source, InputStream target) throws IOException {
		int block = 20;
		byte[] sourceBytes = new byte[block];
		byte[] targetBytes = new byte[block];
		for (;;) {
			int sourceReaded = source.read(sourceBytes);
			int targetReaded = source.read(targetBytes);
			if (sourceReaded < 0 && targetReaded < 0) {
				break;
			}
			System.out.println(diff(output, sourceBytes, 0, sourceReaded, targetBytes, 0, targetReaded));
		}
		// int sourceSize = source.length;
		// int targetSize = target.length;
		// int max = Math.max(sourceSize, targetSize);
		// for (int n = 0; n < max; n += block) {
		// int sourceBlock = Math.min(block, Math.max(0, sourceSize - n));
		// int targetBlock = Math.min(block, Math.max(0, targetSize - n));
		// byte[] sourceTmp = new byte[sourceBlock];
		// byte[] targetTmp = new byte[targetBlock];
		// if (sourceBlock > 0) {
		// System.arraycopy(source, n, sourceTmp, 0, sourceBlock);
		// }
		// if (targetBlock > 0) {
		// System.arraycopy(target, n, targetTmp, 0, targetBlock);
		// }
		// System.out.println(diff(output, sourceTmp, 0, targetTmp, 0));
		// }
		System.out.println("Total: " + output.size() / 2);
	}

	// public static int diff(byte[] source, int sourceIndex, int sourceLength,
	// byte[] target, int targetIndex, int targetLength) {
	// if (sourceIndex == sourceLength && targetIndex != targetLength) {
	// return targetLength - targetIndex;
	// } else if (sourceIndex != sourceLength && targetIndex == targetLength) {
	// return sourceLength - sourceIndex;
	// } else if (sourceIndex == sourceLength && targetIndex == targetLength) {
	// return 0;
	// } else if (source[sourceIndex] == target[targetIndex]) {
	// return diff(source, sourceIndex + 1, target, targetIndex + 1);
	// } else {
	// int remove = 1 + diff(source, sourceIndex + 1, sourceLength, target,
	// targetIndex, targetLength);
	// int add = 1 + diff(source, sourceIndex, target, targetIndex + 1);
	// if (remove <= add) {
	// return 1 + diff(source, sourceIndex + 1, sourceLength, target,
	// targetIndex, targetLength);
	// } else {
	// return 1 + diff(source, sourceIndex, sourceLength, target, targetIndex +
	// 1, targetLength);
	// }
	// }
	// }

	public static int diff(DataOutputStream output, byte[] source, int sourceIndex, int sourceSize, byte[] target, int targetIndex, int targetSize) throws IOException {
		if (sourceIndex == sourceSize && targetIndex != targetSize) {
			if (output != null) {
				output.writeBoolean(true);
				output.writeByte(target[targetIndex]);
			}
			return 1 + diff(output, source, sourceIndex, sourceSize, target, targetIndex + 1, targetSize);
		} else if (sourceIndex != sourceSize && targetIndex == targetSize) {
			if (output != null) {
				output.writeBoolean(false);
				output.writeByte(source[sourceIndex]);
			}
			return 1 + diff(output, source, sourceIndex + 1, sourceSize, target, targetIndex, targetSize);
		} else if (sourceIndex == sourceSize && targetIndex == targetSize) {
			return 0;
		} else if (source[sourceIndex] == target[targetIndex]) {
			return diff(output, source, sourceIndex + 1, sourceSize, target, targetIndex + 1, targetSize);
		} else {
			int remove = diff(null, source, sourceIndex + 1, sourceSize, target, targetIndex, targetSize);
			int add = diff(null, source, sourceIndex, sourceSize, target, targetIndex + 1, targetSize);
			if (remove <= add) {
				if (output != null) {
					output.writeBoolean(false);
					output.writeByte(source[sourceIndex]);
				}
				return 1 + diff(output, source, sourceIndex + 1, sourceSize, target, targetIndex, targetSize);
			} else {
				if (output != null) {
					output.writeBoolean(true);
					output.writeByte(target[targetIndex]);
				}
				return 1 + diff(output, source, sourceIndex, sourceSize, target, targetIndex + 1, targetSize);
			}
		}
	}

	// public static int diff_tmp(DataOutputStream output, byte[] source, byte[]
	// target) throws IOException {
	// Stack<Integer> countStack = new Stack<Integer>();
	// Stack<Integer> sourceStack = new Stack<Integer>();
	// Stack<Integer> targetStack = new Stack<Integer>();
	// Stack<DataOutputStream> outputStack = new Stack<DataOutputStream>();
	// sourceStack.push(0);
	// targetStack.push(0);
	// return diff_tmp(output, countStack, sourceStack, targetStack,
	// outputStack, source, target);
	// }

	// public static int diff_tmp(DataOutputStream output, Stack<Integer>
	// countStack, Stack<Integer> sourceStack, Stack<Integer> targetStack,
	// Stack<DataOutputStream> outputStack, byte[] source, byte[] target) throws
	// IOException {
	// while (!sourceStack.empty() && !targetStack.empty()) {
	// if (countStack.size() == 1) {
	// return countStack.pop();
	// }
	// int sourceIndex = sourceStack.pop();
	// int targetIndex = targetStack.pop();
	// if (sourceIndex == source.length && targetIndex != target.length) {
	// return target.length - targetIndex;
	// } else if (sourceIndex != source.length && targetIndex == target.length)
	// {
	// return source.length - sourceIndex;
	// } else if (sourceIndex == source.length && targetIndex == target.length)
	// {
	// countStack.push(0);
	// } else if (source[sourceIndex] == target[targetIndex]) {
	// sourceStack.push(sourceIndex);
	// targetStack.push(targetIndex);
	// sourceStack.push(sourceIndex + 1);
	// targetStack.push(targetIndex + 1);
	// outputStack.push(output);
	// } else {
	// {
	// sourceStack.push(sourceIndex + 1);
	// targetStack.push(targetIndex);
	// outputStack.push(null);
	// }
	// {
	// sourceStack.push(sourceIndex);
	// targetStack.push(targetIndex + 1);
	// outputStack.push(null);
	// }
	// int remove = 1 + diff(null, source, sourceIndex + 1, target,
	// targetIndex);
	// int add = 1 + diff(null, source, sourceIndex, target, targetIndex + 1);
	// if (remove <= add) {
	// if (output != null) {
	// output.writeBoolean(false);
	// output.writeByte(source[sourceIndex]);
	// }
	// return 1 + diff(output, source, sourceIndex + 1, target, targetIndex);
	// } else {
	// if (output != null) {
	// output.writeBoolean(true);
	// output.writeByte(target[sourceIndex]);
	// }
	// return 1 + diff(output, source, sourceIndex, target, targetIndex + 1);
	// }
	// }
	// }
	// return countStack.pop();
	// }

	public static void main(String[] args) throws IOException, DeltaException, PatchException {
		byte[] sourceBytes = new byte[] { 'a', 'b', 'c', 'd', 'f', 'g', 'h', 'j', 'q', 'z' };
		byte[] targetBytes = new byte[] { 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'i', 'j', 'k', 'r', 'x', 'y', 'z' };
		ByteArrayInputStream source = new ByteArrayInputStream(sourceBytes);
		ByteArrayInputStream target = new ByteArrayInputStream(targetBytes);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ByteArrayOutputStream last = new ByteArrayOutputStream();
		// diff(new DataOutputStream(output), sourceBytes, targetBytes);
		File sourceFile = new File("eclipse-32.zip");
		File targetFile = new File("eclipse-64.zip");
		// sourceBytes = new byte[(int) sourceFile.length()];
		// targetBytes = new byte[(int) targetFile.length()];
		// {
		// FileInputStream stream = new FileInputStream(sourceFile);
		// for (int n = 0; n < sourceBytes.length; n++) {
		// sourceBytes[n] = (byte) stream.read();
		// }
		// }
		// {
		// FileInputStream stream = new FileInputStream(targetFile);
		// for (int n = 0; n < targetBytes.length; n++) {
		// targetBytes[n] = (byte) stream.read();
		// }
		// }
		diff(null, new FileInputStream(sourceFile), new FileInputStream(targetFile));
		diff(new DataOutputStream(output), new FileInputStream(sourceFile), new FileInputStream(targetFile));
		// diff(new DataOutputStream(output), new byte[1024 * 1024], new
		// byte[1024 * 1024]);
		// computeDelta(source, sourceBytes.length, target, targetBytes.length,
		// new GDiffWriter(new DataOutputStream(output)));
		// new GDiffPatcher(new ByteArraySeekableSource(sourceBytes), new
		// ByteArrayInputStream(output.toByteArray()), last);
		// Assert.assertArrayEquals(targetBytes, last.toByteArray());
	}

}
