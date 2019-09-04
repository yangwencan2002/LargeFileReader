
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

import com.vincan.LargeFileReader;
import com.vincan.LargeFileReader.FileSlice;
import com.vincan.LargeFileReader.OnReadFileListener;
import com.vincan.reverser.IStringReverser;
import com.vincan.reverser.ReverserBuilder;

/**
 * 
 * @author wencanyang
 *
 */
public class Main {

	private final static boolean overwriteInputFile = true;

	public static void main(String[] args) throws Exception {
		String outputFilePath = "res" + File.separator + "output.txt";
		File outputFile = new File(outputFilePath);
		if (outputFile.exists()) {
			outputFile.delete();
			outputFile.createNewFile();
		}
		final RandomAccessFile outputRandomAccessFile = new RandomAccessFile(outputFile, "rw");
		final FileChannel outputFileChannel = outputRandomAccessFile.getChannel();

		LargeFileReader.Builder builder = new LargeFileReader.Builder("res" + File.separator + "input.txt");
		builder.threadSize(5).charset("UTF-8").bufferSize(1024 * 1024);
		LargeFileReader hugeFileReader = builder.build();
		final long inputFileLength = hugeFileReader.getInputFile().length();
		IStringReverser lineReverser = ReverserBuilder.buildReverser(ReverserBuilder.TYPE_STRINGBUILDER);
		hugeFileReader.setOnReadFileListener(new OnReadFileListener() {

			@Override
			public void onStart() {

			}

			@Override
			public synchronized void onSlice(FileSlice fileSlice) throws IOException {
				long outputStart = inputFileLength - fileSlice.start - fileSlice.size;
				MappedByteBuffer outputMappedByteBuffer = outputFileChannel.map(MapMode.READ_WRITE, outputStart,
						fileSlice.size);
				for (int i = fileSlice.lines.size() - 1; i >= 0; i--) {// 倒序
					String line = fileSlice.lines.get(i);
					line = lineReverser.reverse(line);
					outputMappedByteBuffer.put(line.getBytes("UTF-8"));
				}
			}

			@Override
			public void onEnd() {
				try {
					if (outputFileChannel != null) {
						outputFileChannel.close();
					}
					if (outputRandomAccessFile != null) {
						outputRandomAccessFile.close();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				if (overwriteInputFile) {
					File inputFile = hugeFileReader.getInputFile();
					inputFile.delete();
					outputFile.renameTo(inputFile);
				}
			}

		});
		hugeFileReader.execute();
	}

}
