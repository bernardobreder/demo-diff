import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class MyDiff {

	private DataOutputStream output;
	private final InputStream source;
	private final InputStream target;
	private int block = 100;
	private byte[] sourceBytes;
	private byte[] targetBytes;
	private int sourceSize;
	private int targetSize;

	public MyDiff(OutputStream output, InputStream source, InputStream target) {
		this.output = new DataOutputStream(output);
		this.source = source;
		this.target = target;
	}

	public void diff() throws IOException {
		sourceBytes = new byte[block];
		targetBytes = new byte[block];
		for (;;) {
			this.sourceSize = source.read(sourceBytes);
			this.targetSize = target.read(targetBytes);
			if (sourceSize < 0 && targetSize < 0) {
				System.out.println(block);
				return;
			}
			if (sourceSize < 0) {
				sourceSize = 0;
			}
			if (targetSize < 0) {
				targetSize = 0;
			}
			this.diff(0, 0);
			System.out.println(this.output.size());
		}
	}

	public void sdiff() throws IOException {
		{
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			for (int n; ((n = this.source.read()) != -1);) {
				output.write((char) n);
			}
			this.sourceBytes = output.toByteArray();
			this.sourceSize = sourceBytes.length;
		}
		{
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			for (int n; ((n = this.target.read()) != -1);) {
				output.write((char) n);
			}
			this.targetBytes = output.toByteArray();
			this.targetSize = targetBytes.length;
		}
		Integer[][] cache = new Integer[2][this.sourceSize];
		for (int n = this.targetSize - 1; n >= 0; n--) {
			this.sdiff(cache, n, 0, n, n);
			System.arraycopy(cache[0], 0, cache[1], 0, this.sourceSize);
			Arrays.fill(cache[0], null);
		}
	}

	private int sdiff(Integer[][] cache, int offx, int offy, int sourceIndex, int targetIndex) throws IOException {
		if (sourceIndex == sourceSize || targetIndex == targetSize) {
			return 0;
		}
		Integer result = cache[offy][offx];
		if (result == null) {
			sdiff(cache, offx + 1, offy, sourceIndex + 1, targetIndex);
			sdiff(cache, offx, offy + 1, sourceIndex, targetIndex + 1);
			if (sourceBytes[sourceIndex] == targetBytes[targetIndex]) {
				result = sdiff(cache, offx + 1, offy + 1, sourceIndex + 1, targetIndex + 1);
			} else {
				int remove = sdiff(cache, offx + 1, offy, sourceIndex + 1, targetIndex);
				int add = sdiff(cache, offx, offy + 1, sourceIndex, targetIndex + 1);
				if (remove <= add) {
					if (this.output != null) {
						output.writeBoolean(false);
						output.writeByte(sourceBytes[sourceIndex]);
					}
					result = 1 + remove;
				} else {
					if (this.output != null) {
						output.writeBoolean(true);
						output.writeByte(targetBytes[targetIndex]);
					}
					result = 1 + remove;
				}
			}
			cache[offy][offx] = result;
		}
		return result;
	}

	private int diff(int sourceIndex, int targetIndex) throws IOException {
		if (sourceIndex == sourceSize && targetIndex != targetSize) {
			if (this.output != null) {
				output.writeBoolean(true);
				output.writeByte(targetBytes[targetIndex]);
			}
			return 1 + diff(sourceIndex, targetIndex + 1);
		} else if (sourceIndex != sourceSize && targetIndex == targetSize) {
			if (this.output != null) {
				output.writeBoolean(false);
				output.writeByte(sourceBytes[sourceIndex]);
			}
			return 1 + diff(sourceIndex + 1, targetIndex);
		} else if (sourceIndex == sourceSize && targetIndex == targetSize) {
			return 0;
		} else if (sourceBytes[sourceIndex] == targetBytes[targetIndex]) {
			return diff(sourceIndex + 1, targetIndex + 1);
		} else {
			int remove = simule(sourceIndex + 1, targetIndex);
			int add = simule(sourceIndex, targetIndex + 1);
			if (remove <= add) {
				if (this.output != null) {
					output.writeBoolean(false);
					output.writeByte(sourceBytes[sourceIndex]);
				}
				return 1 + diff(sourceIndex + 1, targetIndex);
			} else {
				if (this.output != null) {
					output.writeBoolean(true);
					output.writeByte(targetBytes[targetIndex]);
				}
				return 1 + diff(sourceIndex, targetIndex + 1);
			}
		}
	}

	private int simule(int sourceIndex, int targetIndex) throws IOException {
		if (sourceIndex == sourceSize) {
			if (targetIndex == targetSize) {
				return 0;
			} else {
				return targetSize - targetIndex;
			}
		} else {
			if (targetIndex == targetSize) {
				return sourceSize - sourceIndex;
			} else {
				if (sourceBytes[sourceIndex] == targetBytes[targetIndex]) {
					return simule(sourceIndex + 1, targetIndex + 1);
				} else {
					int remove = simule(sourceIndex + 1, targetIndex);
					int add = simule(sourceIndex, targetIndex + 1);
					if (remove <= add) {
						return 1 + remove;
					} else {
						return 1 + add;
					}
				}
			}
		}
	}

	private int ssimule(Integer[][] cache, int offx, int offy, int sourceIndex, int targetIndex) throws IOException {
		if (sourceIndex == sourceSize) {
			if (targetIndex == targetSize) {
				return 0;
			} else {
				return targetSize - targetIndex;
			}
		} else {
			if (targetIndex == targetSize) {
				return sourceSize - sourceIndex;
			} else {
				if (sourceBytes[sourceIndex] == targetBytes[targetIndex]) {
					return simule(sourceIndex + 1, targetIndex + 1);
				} else {
					int remove = simule(sourceIndex + 1, targetIndex);
					int add = simule(sourceIndex, targetIndex + 1);
					if (remove <= add) {
						return 1 + remove;
					} else {
						return 1 + add;
					}
				}
			}
		}
	}

	public static void main(String[] args) throws Exception {
		// File sourceFile = new File("eclipse-32.zip");
		// File sourceFile = new File("build.xml");
		// File targetFile = new File("eclipse-64.zip");
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		// new MyDiff(output, new FileInputStream(sourceFile), new
		// FileInputStream(targetFile)).diff();
		new MyDiff(output, new ByteArrayInputStream("cbc".getBytes()), new ByteArrayInputStream("abc".getBytes())).sdiff();
		System.out.println(Arrays.toString(output.toByteArray()));
	}

}
