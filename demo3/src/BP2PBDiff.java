import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import at.spardat.xma.xdelta.JarDelta;
import at.spardat.xma.xdelta.JarPatcher;

import com.nothome.delta.DeltaException;

public class BP2PBDiff {

	/**
	 * Comprime os dados
	 * 
	 * @param bytes
	 * @param files
	 * @throws IOException
	 */
	public static void zip(OutputStream bytes, File... files) throws IOException {
		ZipOutputStream output = new ZipOutputStream(bytes);
		List<File> list = new ArrayList<File>();
		for (File file : files) {
			list.add(file);
		}
		while (list.size() > 0) {
			File file = list.remove(0);
			JarEntry entry = new JarEntry(file.toString());
			entry.setTime(file.lastModified());
			output.putNextEntry(entry);
			output.closeEntry();
			if (file.isDirectory() && !file.isHidden()) {
				File[] children = file.listFiles();
				if (children != null) {
					for (File child : children) {
						list.add(child);
					}
				}
			}
		}
		output.close();
	}

	/**
	 * Cria um diff dos arquivos
	 * 
	 * @param source
	 * @param target
	 * @param output
	 * @throws IOException
	 * @throws DeltaException
	 */
	public static void patch(ZipFile source, ZipFile target, OutputStream output) throws IOException, DeltaException {
		new JarDelta().computeDelta(source, target, new ZipOutputStream(output));
	}

	/**
	 * Aplica a diferença do arquivo original para o novo
	 * 
	 * @param source
	 * @param patch
	 * @param output
	 * @throws IOException
	 * @throws DeltaException
	 */
	public static void apply(ZipFile source, ZipFile patch, OutputStream output) throws IOException, DeltaException {
		new JarPatcher().applyDelta(source, patch, new ZipOutputStream(output));
	}

	public static void main(String[] args) throws Exception {
		patch(new ZipFile("eclipse-32.zip"), new ZipFile("eclipse-64.zip"), new FileOutputStream("a.diff"));
		apply(new ZipFile("eclipse-32.zip"), new ZipFile("a.diff"), new FileOutputStream("eclipse.zip"));
	}

}
