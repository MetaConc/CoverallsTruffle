/**
 * Copyright (c) 2016 Carmen Torres López, Stefan Marr
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package coveralls.truffle;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.Builder;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.utilities.JSONHelper;
import com.oracle.truffle.api.utilities.JSONHelper.JSONArrayBuilder;
import com.oracle.truffle.api.utilities.JSONHelper.JSONObjectBuilder;

@Registration(id = Coverage.ID)
public class Coverage extends TruffleInstrument {

  public static final String                ID         = "coverageId";
  private final Map<SourceSection, Counter> statements = new HashMap<>();

  @Override
  protected void onCreate(final Env env) {
    // TODO Auto-generated method stub
    System.out.println("hello world");

    Builder filters = SourceSectionFilter.newBuilder();
    filters.tagIs(StatementTag.class);

    env.getInstrumenter().attachFactory(filters.build(), ctx -> {
      Counter c;
      if (statements.containsKey(ctx.getInstrumentedSourceSection())) {
        c = statements.get(ctx.getInstrumentedSourceSection());

      } else {
        c = new Counter(ctx.getInstrumentedSourceSection());
        statements.put(ctx.getInstrumentedSourceSection(), c);
      }

      return new CountingNode(c);
    });

  }

  @Override
  protected void onDispose(final Env env) {
    // TODO Auto-generated method stub

    generateCoverageJson(getCoverageMap());

    super.onDispose(env);
  }

  public Map<Source, Long[]> getCoverageMap() {
    Map<Source, Long[]> coverageMap = new HashMap<>();

    for (Counter counter : statements.values()) {
      Source src = counter.getSourceSection().getSource();
      Long[] array;

      if (coverageMap.containsKey(src)) {
        array = coverageMap.get(src);
      } else if (src.getLineCount() == 0) {
        continue;
      } else {
        array = new Long[src.getLineCount()];
        coverageMap.put(src, array);
      }

      long val = counter.getCounter();
      int line = counter.getSourceSection().getStartLine() - 1;
      if (array[line] == null) {
        array[line] = val;
      } else {
        array[line] = Math.max(val, array[line]);
      }
    }
    return coverageMap;
  }

  public void generateCoverageJson(final Map<Source, Long[]> coverageMap) {

    JSONObjectBuilder coverageRequest = JSONHelper.object();
    coverageRequest.add("service_job_id", "1234567890");
    coverageRequest.add("service-name", "travis-ci");

    JSONArrayBuilder allSourceFiles = JSONHelper.array();

    for (Source s : coverageMap.keySet()) {
      JSONObjectBuilder sourceFile = JSONHelper.object();

      sourceFile.add("name", s.getName());

      sourceFile.add("source_digest", getEncryption(s.getInputStream()));

      sourceFile.add("coverage", getArrayBuilder(coverageMap.get(s)));

      allSourceFiles.add(sourceFile);
    }

    coverageRequest.add("sources_files", allSourceFiles);

    String result = coverageRequest.toString();

    System.out.println("RESULT " + result);

    try {

      PrintWriter printer = new PrintWriter(new File("coverage_som.json"));
      printer.write(result);
      printer.close();

    } catch (FileNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private String getEncryption(final InputStream code) {
    // TODO Auto-generated method stub
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      DigestInputStream dis = new DigestInputStream(code, md);

      byte[] buffer = new byte[1024];
      int numRead;
      do {
        numRead = dis.read(buffer);

      } while (numRead != -1);

      byte[] result = md.digest();
      BigInteger bigInt = new BigInteger(1, result);
      String str = bigInt.toString(16);

      while (str.length() < 32) {
        str = "0" + str;
      }

      return str;

    } catch (NoSuchAlgorithmException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    return null;
  }

  private JSONArrayBuilder getArrayBuilder(final Long[] values) {
    JSONArrayBuilder array = JSONHelper.array();

    for (Long l : values) {
      array.add(l);
    }

    return array;
  }
}
