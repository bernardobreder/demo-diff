import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import junit.framework.Assert;

import org.junit.Test;

import com.nothome.delta.DeltaException;

public class BP2PBDiffTest {

	@Test
	public void zip() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		BP2PBDiff.zip(bytes, new File("lib"));
		ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()));
		ZipEntry entry = input.getNextEntry();
		Assert.assertEquals("lib", entry.getName());
		input.closeEntry();
		entry = input.getNextEntry();
		Assert.assertEquals("lib/javaxdelta-1.1.jar", entry.getName());
		input.closeEntry();
		input.close();
	}

	@Test
	public void test() throws FileNotFoundException, IOException, DeltaException {
		ZipFile source = new ZipFile("eclipse-32.zip");
		ZipFile target = new ZipFile("eclipse-64.zip");
		BP2PBDiff.patch(source, target, new FileOutputStream("a.diff"));
		BP2PBDiff.apply(source, new ZipFile("a.diff"), new FileOutputStream("eclipse.zip"));
	}

}
