package org.myorg;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;

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

public class WordFreq {

	public static class Map extends MapReduceBase implements
			Mapper<LongWritable, Text, Text, IntWritable> {
		private final static IntWritable one = new IntWritable(1);
		private Text word = new Text();
		private Set<String> genPatternSet = new HashSet<String>();

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
						patternsFile.toString()));
				String pattern = null;
				while ((pattern = fileBuf.readLine()) != null) {
					if (!genPatternSet.contains(pattern)) {
						genPatternSet.add(pattern);
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
				if (genPatternSet.contains(tokenizer)) {
					word.set(tokenizer.nextToken());
					output.collect(word, one);
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

	public static void main(String[] args) throws Exception {
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

		DistributedCache.addCacheFile(new URI("/task3/wordpatterns.txt"), conf);
		JobClient.runJob(conf);
	}
}
