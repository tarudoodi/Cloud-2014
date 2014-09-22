package org.myorg;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.TextInputFormat;
import org.apache.hadoop.mapred.TextOutputFormat;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

public class WordFreq extends Configured implements Tool {

	public static class Map extends MapReduceBase implements
			Mapper<LongWritable, Text, Text, IntWritable> {
		private final static IntWritable one = new IntWritable(1);
		private Text word = new Text();
		private Set<StringTokenizer> genPatternSet = new HashSet<StringTokenizer>();

		public void configure(JobConf job) {

			Path[] patternsFiles;
			try {
				patternsFiles = DistributedCache.getLocalCacheFiles(job);
				genPattern(patternsFiles[0]);
			} catch (IOException ioe) {
				System.err
						.println("Caught exception while getting cached files: "
								+ StringUtils.stringifyException(ioe));
			}
		}

		private void genPattern(Path patternsFile) {
			try {
				BufferedReader fileBuf = new BufferedReader(new FileReader(
						patternsFile.toString())); // converting the entire file
													// to string in the buffered
				String pattern = null;
				while ((pattern = fileBuf.readLine()) != null) {
					if (!genPatternSet.contains(pattern)) {
						StringTokenizer patternTokenizer = new StringTokenizer( //extract tokens and use them to create a hashset of tokens
								pattern);
						while (patternTokenizer.hasMoreElements()) {
							genPatternSet.add(patternTokenizer);
							patternTokenizer.nextToken();
						}
					}
				}
				fileBuf.close();
			} catch (IOException ioe) {
				System.err
						.println("Caught exception while parsing the cached file '"
								+ patternsFile
								+ "' : "
								+ StringUtils.stringifyException(ioe));
			}
		}

		public void map(LongWritable key, Text value,
				OutputCollector<Text, IntWritable> output, Reporter reporter)
				throws IOException {
			String line = value.toString();
			StringTokenizer tokenizer = new StringTokenizer(line);
			while (tokenizer.hasMoreTokens()) {

				if (genPatternSet.contains(tokenizer)) { 	// check whether the current token exists in the pattern hash set
					word.set(tokenizer.nextToken());
					output.collect(word, one);
				} else {
					tokenizer.nextToken();
				}
			}
		}
	}

	public static class Reduce extends MapReduceBase implements
			Reducer<Text, IntWritable, Text, IntWritable> {
		public void reduce(Text key, Iterator<IntWritable> values,
				OutputCollector<Text, IntWritable> output, Reporter reporter)
				throws IOException {
			int sum = 0;
			while (values.hasNext()) {
				sum += values.next().get();
			}
			output.collect(key, new IntWritable(sum));
		}
	}

	//public static void main(String[] args) throws Exception
	 public int run(String[] args) throws Exception {
		JobConf conf = new JobConf(WordFreq.class);
		conf.setJobName("wordfreq");

		conf.setOutputKeyClass(Text.class);
		conf.setOutputValueClass(IntWritable.class);

		conf.setMapperClass(Map.class);
		conf.setCombinerClass(Reduce.class);
		conf.setReducerClass(Reduce.class);

		conf.setInputFormat(TextInputFormat.class);
		conf.setOutputFormat(TextOutputFormat.class);

		FileInputFormat.setInputPaths(conf, new Path(args[1]));
		FileOutputFormat.setOutputPath(conf, new Path(args[2]));

		DistributedCache.addCacheFile(new URI("/wordpatterns.txt"), conf);
		JobClient.runJob(conf);
		return 0;
	}
	
	
	 public static void main(String[] args) throws Exception {
		 	     int res = ToolRunner.run(new Configuration(), new WordFreq(), args);
		 	     System.exit(res);
		 	   }
}
