# ConcurrentUnit 0.0.1

A simple concurrent JUnit test case extension.

## Introduction

ConcurrentUnit allows you to write test cases capable of performing concurrent assertions or waiting for expected operations with failures being properly reported back to the main test thread.

## Usage

threadWait or sleep can be called from the main test thread to wait for some other thread to perform assertions. These operations will block until resume() is called, the operation times out, or a threadAssert call fails.

The threadAssert methods can be used from any thread to perform concurrent assertions. Assertion failures will result in the main thread being interrupted and the failure thrown.

## Examples

ConcurrentUnit allows you to perform assertions from outside the context of the JUnit runner's main thread while the main thread can be made to sleep or wait:

    @Test
    public void shouldSucceed() throws Throwable {
        new Thread(new Runnable() {
            public void run() {
                threadAssertTrue(true);
            }
        }).start();
        threadWait(0);
    }

Failed assertions will be properly reported:

    @Test(expected = AssertionError.class)
    public void shouldFail() throws Throwable {
        new Thread(new Runnable() {
            public void run() {
                threadAssertTrue(false);
            }
        }).start();
        threadWait(0);
    }
    
The main thread can wait to be resumed by a worker, and will throw TimeoutException if a resume does not occur within the wait duration:

    @Test(expected = TimeoutException.class)
    public void sleepShouldSupportTimeouts() throws Throwable {
        new Thread(new Runnable() {
            public void run() {
            	Thread.sleep(1000);
            	resume();
            }
        }).start();
        threadWait(500);
    }
    
The main thread can also be told to wait for n number of resume calls:

    @Test
    public void sleepShouldSupportTimeouts() throws Throwable {
        new Thread(new Runnable() {
            public void run() {
            	for (int i = 0; i < 5; i++)
            		resume();
            }
        }).start();
        threadWait(500, 5);
    }

## References

Thanks to the JSR-166 TCK authors for the initial inspiration.