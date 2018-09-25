package com.wix.ocicat

import java.util.concurrent.TimeUnit

import cats.effect.{Clock, IO}
import cats.implicits._
import com.wix.ocicat.ThrottlerConfig._
import org.scalatest._

import scala.concurrent.duration._

class ThrottlerTest extends FlatSpec with Matchers {

  trait ctx {
    def window: Int

    def limit: Int

    val fakeClock = new FakeTimer()
    val throttler = Throttler[IO, Int](limit every window.millis, fakeClock).unsafeRunSync()
  }

  "Throttler" should "not throttle" in new ctx {
    override def window = 1000

    override def limit = 3

    throttler.throttle(1).replicateA(limit).unsafeRunSync()

    fakeClock.add(window)

    throttler.throttle(1).replicateA(limit).unsafeRunSync()

  }

  it should "not throttle in the next tick" in new ctx {
    override def window = 1000

    override def limit = 3

    fakeClock.time = (fakeClock.time / 1000) * 1000 + 999

    throttler.throttle(1).replicateA(limit).unsafeRunSync()

    fakeClock.time = fakeClock.time + 500
    throttler.throttle(1).unsafeRunSync()

  }
  it should "throttle in case limit of calls is exceeded" in new ctx {
    override def window = 1000

    override def limit = 3


    throttler.throttle(1).replicateA(3).unsafeRunSync()
    assertThrows[ThrottleException] {
      throttler.throttle(1).unsafeRunSync()
    }
  }

  it should "throttle after changing tick" in new ctx {
    override def window = 1000

    override def limit = 3

    throttler.throttle(1).replicateA(limit).unsafeRunSync()
    fakeClock.add(window)
    throttler.throttle(1).replicateA(limit).unsafeRunSync()
    assertThrows[ThrottleException] {
      throttler.throttle(1).unsafeRunSync()
    }
    fakeClock.add(window)
    throttler.throttle(1).unsafeRunSync()

  }

  it should "fail on throttle construction" in {
    val fakeClock = new FakeTimer()

    assertThrows[InvalidConfigException] {
      Throttler[IO, Int](-1 every -1.millis, fakeClock).unsafeRunSync()
    }
  }
}

class FakeTimer(var time: Long = System.currentTimeMillis()) extends Clock[IO] {

  override def realTime(unit: TimeUnit) = IO {
    unit.convert(time, TimeUnit.MILLISECONDS)
  }

  override def monotonic(unit: TimeUnit) = ???

  def add(delta: Int): Unit = {
    time = time + delta.toLong
  }
}
