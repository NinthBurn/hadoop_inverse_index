import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

public class InverseIndex {

	/**
	 * Custom InputFormat that provides the actual line number within the file.
	 * The key is the line number (1-based), and the value is the line content.
	 */
	public static class LineNumberInputFormat extends FileInputFormat<LongWritable, Text> {

		@Override
		public RecordReader<LongWritable, Text> createRecordReader(
				InputSplit split, TaskAttemptContext context) {
			return new LineNumberRecordReader();
		}
	}

	/**
	 * Custom RecordReader that reads lines and tracks their actual line numbers
	 * within the original file.
	 */
	public static class LineNumberRecordReader extends RecordReader<LongWritable, Text> {
		private long start;
		private long pos;
		private long end;
		private FSDataInputStream fileIn;
		private BufferedReader reader;
		private LongWritable key = new LongWritable();
		private Text value = new Text();
		private long lineNumber; // Current line number within this split
		private long firstLineNumber; // The line number of the first line in this split

		@Override
        public void initialize(InputSplit genericSplit, TaskAttemptContext context)
		        throws IOException {
	        FileSplit split = (FileSplit) genericSplit;
	        Configuration job = context.getConfiguration();
	        
	        start = split.getStart();
	        end = start + split.getLength();
	        
	        Path file = split.getPath();
	        FileSystem fs = file.getFileSystem(job);
	        fileIn = fs.open(file);
	        
	        // If this is not the first split, we need to skip the first line and adjust position
	        if (start != 0) {
		        fileIn.seek(start);
		        // Read and discard the first partial line
		        // This line belongs to the previous split
		        String partialLine = fileIn.readLine();
		        // If we couldn't read a line, just use start position
		        pos = fileIn.getPos();

	        } else {
		        fileIn.seek(0);
		        pos = 0;
	        }
	        
	        reader = new BufferedReader(new InputStreamReader(fileIn));
	        
	        // Calculate the first line number for this split
	        // Count newlines in the portion of the file before our split
	        firstLineNumber = 1;
	        if (start != 0) {
		        FSDataInputStream countStream = fs.open(file);
		        try {
			        // Read from beginning up to 'pos' (which is after the partial line)
			        byte[] buffer = new byte[65536];
			        long bytesRead = 0;
			        long bytesToRead = pos;
			        
			        while (bytesRead < bytesToRead) {
				        int toRead = (int) Math.min(buffer.length, bytesToRead - bytesRead);
				        int read = countStream.read(buffer, 0, toRead);
				        if (read <= 0) break;
				        for (int i = 0; i < read; i++) {
					        if (buffer[i] == '\n') {
						        firstLineNumber++;
					        }
				        }
				        bytesRead += read;
			        }
		        } finally {
			        countStream.close();
		        }
	        }
	        
	        lineNumber = firstLineNumber;
        }

		@Override
        public boolean nextKeyValue() throws IOException {
	        if (pos >= end) {
		        return false;
	        }
	        
	        String line = reader.readLine();
	        if (line == null) {
		        return false;
	        }
	        
	        // Update position after reading the line
	        pos = fileIn.getPos();
	        
	        // Only emit the line if it's within our split
	        // (the last line might extend beyond our split boundary)
	        if (pos > end && start != 0) {
		        // If we're past the end and this isn't the first split,
		        // this line belongs to the next split
		        return false;
	        }
	        
	        key.set(lineNumber);
	        value.set(line);
	        lineNumber++;
	        
	        return true;
        }

		@Override
		public LongWritable getCurrentKey() {
			return key;
		}

		@Override
		public Text getCurrentValue() {
			return value;
		}

		@Override
		public float getProgress() throws IOException {
			if (start == end) {
				return 0.0f;
			}
			return Math.min(1.0f, (pos - start) / (float) (end - start));
		}

		@Override
		public void close() throws IOException {
			if (reader != null) {
				reader.close();
			}
		}
	}

	/**
	 * This class counts the occurrences of each word in a set of text files,
	 * excluding common stop words. It outputs the word along with the file name
	 * and the line numbers where the word appears.
	 */
	public static class InverseIndexMapper extends Mapper<LongWritable, Text, Text, Text> {
		private Text keyInfo = new Text();
		private Text valueInfo = new Text();
		private List<String> stopWords;

		@Override
		protected void setup(Context context) {
			InputStream is = this.getClass().getResourceAsStream("/stopwords.txt");
			stopWords = new ArrayList<>();

			try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
				String line;
				while ((line = br.readLine()) != null) {
					stopWords.add(line.trim().toLowerCase());
				}

			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}

		@Override
		public void map(LongWritable key, Text value, Context context)
				throws IOException, InterruptedException {
			FileSplit split = (FileSplit) context.getInputSplit();
			String filename = split.getPath().getName();
			
			// The key from our custom RecordReader is the actual line number
			long lineNumber = key.get();
			String line = value.toString();
			
			if (line.trim().isEmpty()) {
				return;
			}

			StringTokenizer words = new StringTokenizer(
					line,
					" \t\n\r\f\"'’,.()?![]#$*-=;:_+/\\<>@%&|{}^~`«»—0123456789"
			);

			while (words.hasMoreTokens()) {
				String word = words.nextToken().toLowerCase();

				// Remove any remaining non-letter characters
				word = word.replaceAll("[^a-zA-Z]", "");
				
				if (!word.isEmpty() && !stopWords.contains(word)) {
					keyInfo.set(word + ";" + filename);
					valueInfo.set(Long.toString(lineNumber));
					context.write(keyInfo, valueInfo);
				}
			}
		}
	}

	/**
	 * Combiner class to aggregate the line numbers for each word in a file.
	 */
	public static class InverseIndexCombiner extends Reducer<Text, Text, Text, Text> {
		private Text valueInfo = new Text();

		@Override
		protected void reduce(Text key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException {
			List<Long> lineNumbersList = new ArrayList<>();
			for (Text value : values) {
				lineNumbersList.add(Long.parseLong(value.toString()));
			}
			
			Collections.sort(lineNumbersList);
			List<Long> uniqueSortedLines = new ArrayList<>();
			for (Long num : lineNumbersList) {
				if (uniqueSortedLines.isEmpty() || 
					!uniqueSortedLines.get(uniqueSortedLines.size() - 1).equals(num)) {
					uniqueSortedLines.add(num);
				}
			}
			
			StringBuilder lineNumbers = new StringBuilder();
			for (int i = 0; i < uniqueSortedLines.size(); i++) {
				lineNumbers.append(uniqueSortedLines.get(i));

				if (i < uniqueSortedLines.size() - 1) {
					lineNumbers.append(", ");
				}
			}

			String[] parts = key.toString().split(";");
			String word = parts[0];
			String filename = parts[1];

			valueInfo.set("(" + filename + ", " + lineNumbers + ")");
			key.set(word);
			context.write(key, valueInfo);
		}
	}

	/**
	 * Reducer class to aggregate the results from the combiner.
	 */
	public static class InverseIndexReducer extends Reducer<Text, Text, Text, Text> {
		private Text valueInfo = new Text();

		@Override
		protected void reduce(Text key, Iterable<Text> values, Context context)
				throws IOException, InterruptedException {
			Map<String, List<Long>> fileToLines = new HashMap<>();

			for (Text value : values) {
				String s = value.toString().trim();
				if (s.startsWith("(") && s.endsWith(")")) {
					s = s.substring(1, s.length() - 1).trim();
				}

				String[] parts = s.split(",", 2);
				String filename = parts[0].trim();
				String rest = (parts.length > 1) ? parts[1].trim() : "";

				String[] nums = rest.split(",");
				List<Long> list = fileToLines.computeIfAbsent(filename, k -> new ArrayList<>());
				
				for (String num : nums) {
					String trimmed = num.trim();
					if (!trimmed.isEmpty()) {
						list.add(Long.parseLong(trimmed));
					}
				}
			}

			// Sort and deduplicate for each file
			for (List<Long> lines : fileToLines.values()) {
				Collections.sort(lines);
				if (lines.size() > 1) {
					List<Long> uniqueLines = new ArrayList<>();
					uniqueLines.add(lines.get(0));
					for (int i = 1; i < lines.size(); i++) {
						if (!lines.get(i).equals(lines.get(i - 1))) {
							uniqueLines.add(lines.get(i));
						}
					}
					lines.clear();
					lines.addAll(uniqueLines);
				}
			}

			// Build output
			StringBuilder fileList = new StringBuilder();
			List<String> sortedFiles = new ArrayList<>(fileToLines.keySet());
			Collections.sort(sortedFiles);
			
			for (String filename : sortedFiles) {
				List<Long> lines = fileToLines.get(filename);
				StringBuilder joinedNums = new StringBuilder();
				for (int i = 0; i < lines.size(); i++) {
					joinedNums.append(lines.get(i));
					if (i < lines.size() - 1) {
						joinedNums.append(", ");
					}
				}
				fileList
						.append("(")
						.append(filename)
						.append(", ")
						.append(joinedNums)
						.append(") ");
			}

			if (fileList.length() >= 1) {
				fileList.setLength(fileList.length() - 1);
			}

			valueInfo.set(fileList.toString());
			context.write(key, valueInfo);
		}
	}

	/**
	 * Main method to set up and run the Hadoop job.
	 */
	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		Job job = Job.getInstance(conf, "Inverse Index");

		job.setJarByClass(InverseIndex.class);
		job.setMapperClass(InverseIndexMapper.class);
		job.setCombinerClass(InverseIndexCombiner.class);
		job.setReducerClass(InverseIndexReducer.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		
		// Use our custom input format
		job.setInputFormatClass(LineNumberInputFormat.class);

		FileInputFormat.addInputPath(job, new Path(args[0]));
		FileOutputFormat.setOutputPath(job, new Path(args[1]));

		System.exit(job.waitForCompletion(true) ? 0 : 1);
	}
}
