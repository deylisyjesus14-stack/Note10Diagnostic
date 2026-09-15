package com.jesusdroidl.note10optimizer;

/** Resolves Process.myUid() explicitly to Android's process API. */
final class Process {
    private Process() {}
    static int myUid() {
        return android.os.Process.myUid();
    }
}
