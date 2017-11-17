package org.BYUSecureSMS.messaging.util;

import org.BYUSecureSMS.messaging.util.deque.LinkedBlockingDeque;

public class LinkedBlockingLifoQueue<E> extends LinkedBlockingDeque<E> {
  @Override
  public void put(E runnable) throws InterruptedException {
    super.putFirst(runnable);
  }

  @Override
  public boolean add(E runnable) {
    super.addFirst(runnable);
    return true;
  }

  @Override
  public boolean offer(E runnable) {
    super.addFirst(runnable);
    return true;
  }
}
