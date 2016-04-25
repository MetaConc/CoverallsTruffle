package coveralls.truffle;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;

public class CountingNode extends ExecutionEventNode {
  protected final Counter counter;

  public CountingNode(final Counter counter) {
    this.counter = counter;
  }

  @Override
  protected void onEnter(final VirtualFrame frame) {
    counter.counter += 1;
  }

}
