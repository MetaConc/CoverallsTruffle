package coveralls.truffle;

import com.oracle.truffle.api.source.SourceSection;

public class Counter {
  public long counter;
  public final SourceSection sourceSection;

  public Counter(final SourceSection sourceSection) {
    this.counter = 0;
    this.sourceSection = sourceSection;
  }

  public long getCounter() {
    return counter;
  }

  public SourceSection getSourceSection() {
    return sourceSection;
  }
}
