package com.tp3.spmf;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

public class SpmfLcmRunner {

    public static class Result {
        public final long elapsedMillis;
        public final long patternCount;
        public final Path outputFile;

        public Result(long t, long c, Path f) {
            elapsedMillis = t;
            patternCount = c;
            outputFile = f;
        }
    }

    public static Result runLCM(Path jar,
                                Path input,
                                Path output,
                                int minsup) throws Exception {

        long start = System.currentTimeMillis();

        Process p = new ProcessBuilder(
                "java", "-jar", jar.toString(),
                "run", "LCM",
                input.toString(),
                output.toString(),
                String.valueOf(minsup)
        ).redirectErrorStream(true).start();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream()))) {
            while (br.readLine() != null) ;
        }

        p.waitFor();

        long time = System.currentTimeMillis() - start;
        long lines = Files.lines(output).count();

        return new Result(time, lines, output);
    }
}