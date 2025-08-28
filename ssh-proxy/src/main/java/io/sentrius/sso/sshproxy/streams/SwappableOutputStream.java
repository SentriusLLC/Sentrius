package io.sentrius.sso.sshproxy.streams;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicReference;

public final class SwappableOutputStream extends OutputStream {
    private final AtomicReference<OutputStream> delegate = new AtomicReference<>();

    public SwappableOutputStream(OutputStream initial) {
        delegate.set(initial);
    }
    public void set(OutputStream next) { delegate.set(next); }

    @Override public void write(int b) throws IOException { delegate.get().write(b); }
    @Override public void write(byte[] b, int off, int len) throws IOException { delegate.get().write(b, off, len); }
    @Override public void flush() throws IOException { delegate.get().flush(); }

    @Override public void close() throws IOException {
        // Do NOT close the underlying stream here; the SSH layer owns it.
        // You can no-op or close the current delegate if that matches your lifecycle.
    }
}