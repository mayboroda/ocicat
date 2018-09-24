package com.wix.ocicat

import java.util.concurrent.TimeUnit

import cats.effect
import cats.effect.{Clock, IO}
import org.scalatest._
import cats.implicits._
import scala.concurrent.duration._
import com.wix.ocicat.ThrottleConfig._

class ThrottlerTest extends FlatSpec with Matchers {

  trait ctx {
    def window: Int

    def limit: Int

    val fakeTimer = new FakeTimer()
    implicit val cs = fakeTimer.timer
    val throttler = Throttler[IO, Int](limit every window.millis).unsafeRunSync()
  }

  "Throttler" should "not throttle" in new ctx {
    override def window = 1000

    override def limit = 3

    throttler.throttle(1).replicateA(limit).unsafeRunSync()

    fakeTimer.add(window)

    throttler.throttle(1).replicateA(limit).unsafeRunSync()

  }

  it should "not throttle in the next tick" in new ctx {
    override def window = 1000

    override def limit = 3

    fakeTimer.time = (fakeTimer.time / 1000) * 1000 + 999

    throttler.throttle(1).replicateA(limit).unsafeRunSync()

    fakeTimer.time = fakeTimer.time + 500
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
    fakeTimer.add(window)
    throttler.throttle(1).replicateA(limit).unsafeRunSync()
    assertThrows[ThrottleException] {
      throttler.throttle(1).unsafeRunSync()
    }
    fakeTimer.add(window)
    throttler.throttle(1).unsafeRunSync()

  }
}

class FakeTimer(var time: Long = System.currentTimeMillis()) {

  def timer: effect.Timer[IO] = new effect.Timer[IO] {
    override def clock: Clock[IO] = new Clock[IO] {
      override def realTime(unit: TimeUnit) = IO {
        unit.convert(time, TimeUnit.MILLISECONDS)
      }

      override def monotonic(unit: TimeUnit) = ???
    }

    override def sleep(duration: FiniteDuration) = ???
  }

  def add(delta: Int): Unit = {
    time = time + delta.toLong
  }
}