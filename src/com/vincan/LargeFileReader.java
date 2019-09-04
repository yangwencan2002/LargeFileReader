package com.vincan;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import com.vincan.utils.Util;

/**
 * 大文件读取器，内存有限，因此采用分片读取方式，因时间换空间
 * 
 * @author wencanyang
 *
 */
public class LargeFileReader {
	public static final boolean DEBUG = true;
	private final String SEPARATOR = System.getProperty("line.separator");
	private int mThreadSize;// 线程数
	private String mCharset;// 字符集
	private int mBufferSize;// 缓冲区大小
	private File mInputFile;
	private ExecutorService mExecutorService;// 线程池

	private RandomAccessFile mInputRandomAccessFile;
	private FileChannel mInputFileChannel;
	private long mFileLength;
	private Set<FileSliceRange> mFileSliceRangeSet;

	private CyclicBarrier mCyclicBarrier;
	private AtomicLong mLineCounter = new AtomicLong(0);

	private LargeFileReader(File inputFile, String charset, int bufferSize, int threadSize) {
		this.mInputFile = inputFile;
		this.mFileLength = inputFile.length();
		if (this.mFileLength == 0) {
			throw new IllegalArgumentException("Input file is empty.");
		}
		this.mCharset = charset;
		this.mBufferSize = bufferSize;
		this.mThreadSize = threadSize;
		try {
			this.mInputRandomAccessFile = new RandomAccessFile(inputFile, "r");
		} catch (FileNotFoundException e) {
			throw new IllegalArgumentException("Input file do not exist.");
		}
		this.mInputFileChannel = mInputRandomAccessFile.getChannel();
		this.mExecutorService = Executors.newFixedThreadPool(threadSize);
		this.mFileSliceRangeSet = new HashSet<FileSliceRange>();
	}

	/**
	 * 开始执行
	 */
	public void execute() {
		long sliceLength = this.mFileLength / this.mThreadSize;
		try {
			computeFileSliceRange(0, sliceLength);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		final long startTime = System.currentTimeMillis();
		mCyclicBarrier = new CyclicBarrier(mFileSliceRangeSet.size(), new Runnable() {

			@Override
			public void run() {
				if (DEBUG) {
					System.out.println("Total time spend = " + (System.currentTimeMillis() - startTime) + "ms");
					System.out.println("Total lines reversed = " + mLineCounter.get());
				}
				if (mOnReadFileListener != null) {
					mOnReadFileListener.onEnd();
				}
				shutdown();
			}
		});
		if (mOnReadFileListener != null) {
			mOnReadFileListener.onStart();
		}
		for (FileSliceRange fileSliceRange : mFileSliceRangeSet) {
			if (DEBUG) {
				System.out.println("Slice range = " + fileSliceRange);
			}
			this.mExecutorService.execute(new ReadFileSliceTask(fileSliceRange));
		}
	}

	public File getInputFile() {
		return mInputFile;
	}

	/**
	 * 停止执行
	 */
	public void shutdown() {
		try {
			if (mInputFileChannel != null) {
				mInputFileChannel.close();
			}
			if (mInputRandomAccessFile != null) {
				mInputRandomAccessFile.close();
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
		if (mExecutorService != null) {
			mExecutorService.shutdown();
		}
	}

	/**
	 * 计算文件切片范围，按平均分片后，再往后继续读取直到\r\n，即一个完整行结束
	 * 
	 * @param start
	 * @param size
	 * @throws IOException
	 */
	private void computeFileSliceRange(long start, long size) throws IOException {
		if (start > mFileLength - 1) {
			return;
		}
		FileSliceRange range = new FileSliceRange();
		range.start = start;
		long end = start + size - 1;
		if (end >= mFileLength - 1) {
			range.end = mFileLength - 1;
			mFileSliceRangeSet.add(range);
			return;
		}
		mInputRandomAccessFile.seek(end);
		byte tmp = (byte) mInputRandomAccessFile.read();
		while (tmp != '\n' && tmp != '\r') {
			end++;
			if (end >= mFileLength - 1) {
				end = mFileLength - 1;
				break;
			}
			mInputRandomAccessFile.seek(end);
			tmp = (byte) mInputRandomAccessFile.read();
		}
		range.end = end;
		mFileSliceRangeSet.add(range);
		computeFileSliceRange(end + 1, size);
	}

	private String convertBytesToString(byte[] bytes) throws UnsupportedEncodingException {
		if (DEBUG) {
			mLineCounter.incrementAndGet();
		}
		String line = new String(bytes, mCharset);
		if (!Util.isEmpty(line)) {
			return line;
		}
		return "";
	}

	/**
	 * 用于表示文件切片起止位置
	 * 
	 * @author wencanyang
	 *
	 */
	private static final class FileSliceRange {
		public long start;
		public long end;

		@Override
		public boolean equals(Object obj) {
			if (obj == null) {
				return false;
			}
			if (this == obj) {
				return true;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			FileSliceRange other = (FileSliceRange) obj;
			if (end != other.end) {
				return false;
			}
			if (start != other.start) {
				return false;
			}
			return true;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + (int) (end ^ (end >>> 32));
			result = prime * result + (int) (start ^ (start >>> 32));
			return result;
		}

		@Override
		public String toString() {
			return "{start=" + start + ",end=" + end + "}";
		}

	}

	/**
	 * 文件读取回调
	 * 
	 * @author wencanyang
	 *
	 */
	public interface OnReadFileListener {

		/**
		 * 读取开始
		 */
		void onStart();

		/**
		 * 切割
		 * 
		 * @param fileSlice
		 *            切片信息
		 * @throws Exception
		 */
		void onSlice(FileSlice fileSlice) throws IOException;

		/**
		 * 读取结束
		 */
		void onEnd();
	}

	/**
	 * 文件切片信息
	 * 
	 * @author wencanyang
	 *
	 */
	public static final class FileSlice {

		/**
		 * 起始位置
		 */
		public long start;

		/**
		 * 长度
		 */
		public long size;

		/**
		 * 文本内容
		 */
		public List<String> lines;

		public FileSlice(long start, long size, List<String> lines) {
			this.start = start;
			this.size = size;
			this.lines = lines;
		}
	}

	private OnReadFileListener mOnReadFileListener;

	public void setOnReadFileListener(OnReadFileListener onReadFileListener) {
		mOnReadFileListener = onReadFileListener;
	}

	/**
	 * 文件分段读取任务
	 * 
	 * @author wencanyang
	 *
	 */
	private class ReadFileSliceTask implements Runnable {
		private long mStart;
		private long mSliceSize;
		private byte[] mReadBuffer;
		private List<String> lines;

		public ReadFileSliceTask(FileSliceRange range) {
			this.mStart = range.start;
			this.mSliceSize = range.end - range.start + 1;
			this.mReadBuffer = new byte[mBufferSize];
			this.lines = Collections.synchronizedList(new ArrayList<String>());
		}

		@Override
		public void run() {
			try {
				MappedByteBuffer inputMappedByteBuffer = mInputFileChannel.map(MapMode.READ_ONLY, mStart,
						this.mSliceSize);
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				for (int offset = 0; offset < mSliceSize; offset += mBufferSize) {
					int readLength;
					if (offset + mBufferSize <= mSliceSize) {
						readLength = mBufferSize;
					} else {
						readLength = (int) (mSliceSize - offset);
					}
					inputMappedByteBuffer.get(mReadBuffer, 0, readLength);
					for (int i = 0; i < readLength; i++) {
						byte tmp = mReadBuffer[i];
						if (tmp == '\n' || tmp == '\r') {
							String line = convertBytesToString(bos.toByteArray());
							lines.add(line);
							lines.add(SEPARATOR);
							bos.reset();
						} else {
							bos.write(tmp);
						}
					}
				}
				if (bos.size() > 0) {
					String line = convertBytesToString(bos.toByteArray());
					lines.add(line);
				}

				if (mOnReadFileListener != null) {
					mOnReadFileListener.onSlice(new FileSlice(this.mStart, this.mSliceSize, lines));
				}
				mCyclicBarrier.await();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 多参数，因此用builder模式包裹
	 * 
	 * @author wencanyang
	 *
	 */
	public static final class Builder {
		private int mThreadSize = 1;// 线程数
		private String mCharset = Charset.defaultCharset().name();// 字符集
		private int mBufferSize = 1024 * 1024;// 缓冲区大小
		private File mInputFile;

		/**
		 * 文件路径
		 * 
		 * @param inputFilePath
		 */
		public Builder(String inputFilePath) {
			if (Util.isEmpty(inputFilePath)) {
				throw new IllegalArgumentException("Input file path can not be empty.");
			}
			this.mInputFile = new File(inputFilePath);
			if (!this.mInputFile.exists()) {
				throw new IllegalArgumentException("Input file do not exist.");
			}
		}

		/**
		 * 线程数
		 * 
		 * @param threadSize
		 * @return
		 */
		public Builder threadSize(int threadSize) {
			if (threadSize < 1) {
				throw new IllegalArgumentException("Thread size must be more than 1.");
			}
			this.mThreadSize = threadSize;
			return this;
		}

		/**
		 * 字符集
		 * 
		 * @param charset
		 * @return
		 */
		public Builder charset(String charset) {
			if (Util.isEmpty(charset)) {
				throw new IllegalArgumentException("Charset must not be empty.");
			}
			this.mCharset = charset;
			return this;
		}

		/**
		 * 缓冲区大小
		 * 
		 * @param bufferSize
		 * @return
		 */
		public Builder bufferSize(int bufferSize) {
			if (bufferSize < 0) {
				throw new IllegalArgumentException("Buffer size must be more than 0.");
			}
			this.mBufferSize = bufferSize;
			return this;
		}

		public LargeFileReader build() {
			return new LargeFileReader(this.mInputFile, this.mCharset, this.mBufferSize, this.mThreadSize);
		}
	}
}
