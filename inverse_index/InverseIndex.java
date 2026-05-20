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
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

public class InverseIndex {

    /**
     * First Mapper: Emits each line with its byte offset as part of the value.
     * Input:  byteOffset, lineContent
     * Output: filename, byteOffset + \t + lineContent
     */
    public static class LineNumberMapper extends Mapper<LongWritable, Text, Text, Text> {
        private Text outputKey = new Text();
        private Text outputValue = new Text();

        @Override
        protected void setup(Context context) throws IOException, InterruptedException {
        }

        @Override
        public void map(LongWritable key, Text value, Context context)
                throws IOException, InterruptedException {
            String filename = ((FileSplit) context.getInputSplit()).getPath().getName();
            
            outputKey.set(filename);
            // Emit the byte offset along with the line content
            outputValue.set(key.get() + "\t" + value.toString());
            context.write(outputKey, outputValue);
        }
    }

    /**
     * First Reducer: Receives all lines from a single file (due to shuffle on filename),
     * sorts them by byte offset, and assigns final global line numbers.
     * Input:  filename, [byteOffset + \t + lineContent, ...]
     * Output: globalLineNumber, filename\tlineContent
     */
    public static class LineNumberReducer extends Reducer<Text, Text, Text, Text> {
        private Text outputKey = new Text();
        private Text outputValue = new Text();

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {
            // Store all lines with their byte offsets
            List<LineWithOffset> lines = new ArrayList<>();
            for (Text value : values) {
                String[] parts = value.toString().split("\t", 2);
                if (parts.length == 2) {
                    lines.add(new LineWithOffset(Long.parseLong(parts[0]), parts[1]));
                }
            }
            
            // Sort by byte offset - this restores the original file order
            Collections.sort(lines);
            
            // Assign global line numbers starting from 1
            long globalLineNumber = 1;
            String filename = key.toString();
            
            for (LineWithOffset line : lines) {
                outputKey.set(String.valueOf(globalLineNumber));
                outputValue.set(filename + "\t" + line.content);
                context.write(outputKey, outputValue);
                globalLineNumber++;
            }
        }
        
        /**
         * Helper class to keep byte offset and content together for sorting.
         */
        private static class LineWithOffset implements Comparable<LineWithOffset> {
            long offset;
            String content;
            
            LineWithOffset(long offset, String content) {
                this.offset = offset;
                this.content = content;
            }
            
            @Override
            public int compareTo(LineWithOffset other) {
                return Long.compare(this.offset, other.offset);
            }
        }
    }

    /**
     * Second Mapper: Extracts words from line-numbered lines.
     * Input:  globalLineNumber, filename\tlineContent
     * Output: word;filename, lineNumber
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
            // Parse the intermediate format: lineNumber\tfilename\tlineContent
            String[] parts = value.toString().split("\t", 3);
            if (parts.length < 3) {
                return;
            }
            
            String lineNumber = parts[0];
            String filename = parts[1];
            String line = parts[2];
            
            if (line.trim().isEmpty()) {
                return;
            }

            StringTokenizer words = new StringTokenizer(
                    line,
                    " \t\n\r\f\"'’,.()?![]#$*-=;:_+/\\<>@%&|{}^~`«»—0123456789"
            );

            while (words.hasMoreTokens()) {
                String word = words.nextToken().toLowerCase();
                word = word.replaceAll("[^a-zA-Z]", "");
                
                if (!word.isEmpty() && !stopWords.contains(word)) {
                    keyInfo.set(word + ";" + filename);
                    valueInfo.set(lineNumber);
                    context.write(keyInfo, valueInfo);
                }
            }
        }
    }

    /**
     * Combiner: Aggregates line numbers for each word-file combination.
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
     * Final Reducer: Builds the complete inverted index.
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

            // Sort and deduplicate
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
                fileList.append("(")
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

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: InverseIndex <input> <output>");
            System.exit(1);
        }

        Configuration conf = new Configuration();
        
        // Temporary directory for intermediate results
        Path tempDir = new Path("temp_line_numbers_" + System.currentTimeMillis());

        // Job 1: Assign global line numbers using byte offsets
        Job lineNumberJob = Job.getInstance(conf, "Assign Line Numbers");
        lineNumberJob.setJarByClass(InverseIndex.class);
        lineNumberJob.setMapperClass(LineNumberMapper.class);
        lineNumberJob.setReducerClass(LineNumberReducer.class);
        lineNumberJob.setOutputKeyClass(Text.class);
        lineNumberJob.setOutputValueClass(Text.class);
        lineNumberJob.setInputFormatClass(TextInputFormat.class);
        lineNumberJob.setOutputFormatClass(TextOutputFormat.class);
        
        FileInputFormat.addInputPath(lineNumberJob, new Path(args[0]));
        FileOutputFormat.setOutputPath(lineNumberJob, tempDir);

        boolean lineNumberSuccess = lineNumberJob.waitForCompletion(true);
        if (!lineNumberSuccess) {
            System.exit(1);
        }

        // Job 2: Build inverted index
        Job indexJob = Job.getInstance(conf, "Build Inverted Index");
        indexJob.setJarByClass(InverseIndex.class);
        indexJob.setMapperClass(InverseIndexMapper.class);
        indexJob.setCombinerClass(InverseIndexCombiner.class);
        indexJob.setReducerClass(InverseIndexReducer.class);
        indexJob.setOutputKeyClass(Text.class);
        indexJob.setOutputValueClass(Text.class);
        indexJob.setInputFormatClass(TextInputFormat.class);
        indexJob.setOutputFormatClass(TextOutputFormat.class);
        
        FileInputFormat.addInputPath(indexJob, tempDir);
        FileOutputFormat.setOutputPath(indexJob, new Path(args[1]));

        int exitCode = indexJob.waitForCompletion(true) ? 0 : 1;
        
        // Clean up temp directory
        tempDir.getFileSystem(conf).delete(tempDir, true);
        
        System.exit(exitCode);
    }
}
