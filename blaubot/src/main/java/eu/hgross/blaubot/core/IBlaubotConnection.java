package eu.hgross.blaubot.core;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;

import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionListener;

/**
 * A connection abstraction.
 *
 * First of all an implementation has to offer read and write methods similiar
 * to the well known {@link Socket} class as well as the convenience readFully
 * methods known from the {@link DataInputStream} class.
 *
 * Furthermore an implementation MUST inform it's {@link IBlaubotConnectionListener}s
 * if this connection disconnects - whether from an intended disconnect or a
 * connection loss.
 *
 * A disconnected connection object is not of further interest to blaubot and
 * should to be thought of as dead and ready for garbage collection.
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public interface IBlaubotConnection {
    /**
     * Disconnects the connection (if connected), nothing otherwise
     */
    void disconnect();

    boolean isConnected();

    /**
     * Adds a connection listener to get informed if the connection gets closed.
     *
     * @param listener the listener to add
     */
    void addConnectionListener(IBlaubotConnectionListener listener);

    /**
     * Removes a connection listener. If the listener was not attached, the call
     * does nothing.
     *
     * @param listener the previoulsy attached listener
     */
    void removeConnectionListener(IBlaubotConnectionListener listener);

    /**
     * The remote device connected to our device.
     *
     * @return the remote device
     */
    IBlaubotDevice getRemoteDevice();

    /**
     * Writes the specified byte to this output stream. The general
     * contract for <code>write</code> is that one byte is written
     * to the output stream. The byte to be written is the eight
     * low-order bits of the argument <code>b</code>. The 24
     * high-order bits of <code>b</code> are ignored.
     *
     *  Subclasses of <code>OutputStream</code> must provide an
     * implementation for this method.
     *
     * @param b the <code>byte</code>.
     * @throws IOException if an I/O error occurs. In particular,
     *                     an <code>IOException</code> may be thrown if the
     *                     output stream has been closed.
     * @throws SocketTimeoutException if the socket timed out
     */
    void write(int b) throws SocketTimeoutException, IOException;

    /**
     * Writes <code>b.length</code> bytes from the specified byte array
     * to this output stream. The general contract for <code>write(b)</code>
     * is that it should have exactly the same effect as the call
     * <code>write(b, 0, b.length)</code>.
     *
     * @param bytes the data.
     * @throws IOException if an I/O error occurs.
     * @throws SocketTimeoutException if the socket timed out
     * @see java.io.OutputStream#write(byte[], int, int)
     */
    void write(byte[] bytes) throws SocketTimeoutException, IOException;

    /**
     * Writes <code>len</code> bytes from the specified byte array
     * starting at offset <code>off</code> to this output stream.
     * The general contract for <code>write(b, off, len)</code> is that
     * some of the bytes in the array <code>b</code> are written to the
     * output stream in order; element <code>b[off]</code> is the first
     * byte written and <code>b[off+len-1]</code> is the last byte written
     * by this operation.
     *
     *  The <code>write</code> method of <code>OutputStream</code> calls
     * the write method of one argument on each of the bytes to be
     * written out. Subclasses are encouraged to override this method and
     * provide a more efficient implementation.
     *
     *  If <code>b</code> is <code>null</code>, a
     * <code>NullPointerException</code> is thrown.
     *
     *  If <code>off</code> is negative, or <code>len</code> is negative, or
     * <code>off+len</code> is greater than the length of the array
     * <code>b</code>, then an <tt>IndexOutOfBoundsException</tt> is thrown.
     *
     * @param bytes      the data.
     * @param byteOffset the start offset in the data.
     * @param byteCount  the number of bytes to write.
     * @throws IOException if an I/O error occurs. In particular,
     *                     an <code>IOException</code> is thrown if the output
     *                     stream is closed.
     * @throws SocketTimeoutException if the socket timed out
     */
    void write(byte[] bytes, int byteOffset, int byteCount) throws SocketTimeoutException, IOException;


    /**
     * Reads the next byte of data from the input stream. The value byte is
     * returned as an <code>int</code> in the range <code>0</code> to
     * <code>255</code>. If no byte is available because the end of the stream
     * has been reached, the value <code>-1</code> is returned. This method
     * blocks until input data is available, the end of the stream is detected,
     * or an exception is thrown.
     *
     * A subclass must provide an implementation of this method.
     *
     * @return the next byte of data, or <code>-1</code> if the end of the
     * stream is reached.
     * @throws IOException if an I/O error occurs.
     * @throws SocketTimeoutException if the socket timed out
     */
    int read() throws SocketTimeoutException, IOException;

    /**
     * Reads some number of bytes from the contained input stream and
     * stores them into the buffer array <code>b</code>. The number of
     * bytes actually read is returned as an integer. This method blocks
     * until input data is available, end of file is detected, or an
     * exception is thrown.
     * 
     * If <code>b</code> is null, a <code>NullPointerException</code> is
     * thrown. If the length of <code>b</code> is zero, then no bytes are
     * read and <code>0</code> is returned; otherwise, there is an attempt
     * to read at least one byte. If no byte is available because the
     * stream is at end of file, the value <code>-1</code> is returned;
     * otherwise, at least one byte is read and stored into <code>b</code>.
     *
     * <p>The first byte read is stored into element <code>b[0]</code>, the
     * next one into <code>b[1]</code>, and so on. The number of bytes read
     * is, at most, equal to the length of <code>b</code>. Let <code>k</code>
     * be the number of bytes actually read; these bytes will be stored in
     * elements <code>b[0]</code> through <code>b[k-1]</code>, leaving
     * elements <code>b[k]</code> through <code>b[b.length-1]</code>
     * unaffected.
     * </p>
     * 
     * <p>The <code>read(b)</code> method has the same effect as:
     * <blockquote><pre>
     * read(b, 0, b.length)
     * </pre></blockquote>
     *
     * @param buffer the buffer into which the data is read.
     * @return the total number of bytes read into the buffer, or
     * <code>-1</code> if there is no more data because the end
     * of the stream has been reached.
     * @throws IOException if the first byte cannot be read for any reason
     *                     other than end of file, the stream has been closed and the underlying
     *                     input stream does not support reading after close, or another I/O
     *                     error occurs.
     * @see java.io.FilterInputStream#in
     * @see java.io.InputStream#read(byte[], int, int)
     * @throws SocketTimeoutException if the socket timed out
     */
    int read(byte[] buffer) throws SocketTimeoutException, IOException;

    /**
     * Reads up to <code>len</code> bytes of data from the contained
     * input stream into an array of bytes.  An attempt is made to read
     * as many as <code>len</code> bytes, but a smaller number may be read,
     * possibly zero. The number of bytes actually read is returned as an
     * integer.
     *
     * <p> This method blocks until input data is available, end of file is
     * detected, or an exception is thrown.
     * </p>
     * <p> If <code>len</code> is zero, then no bytes are read and
     * <code>0</code> is returned; otherwise, there is an attempt to read at
     * least one byte. If no byte is available because the stream is at end of
     * file, the value <code>-1</code> is returned; otherwise, at least one
     * byte is read and stored into <code>b</code>.
     * </p>
     * <p> The first byte read is stored into element <code>b[off]</code>, the
     * next one into <code>b[off+1]</code>, and so on. The number of bytes read
     * is, at most, equal to <code>len</code>. Let <i>k</i> be the number of
     * bytes actually read; these bytes will be stored in elements
     * <code>b[off]</code> through <code>b[off+</code><i>k</i><code>-1]</code>,
     * leaving elements <code>b[off+</code><i>k</i><code>]</code> through
     * <code>b[off+len-1]</code> unaffected.
     * </p>
     * <p> In every case, elements <code>b[0]</code> through
     * <code>b[off]</code> and elements <code>b[off+len]</code> through
     * <code>b[b.length-1]</code> are unaffected.
     *
     * @param buffer     the buffer into which the data is read.
     * @param byteOffset the start offset in the destination array <code>b</code>
     * @param byteCount  the maximum number of bytes read.
     * @return the total number of bytes read into the buffer, or
     * <code>-1</code> if there is no more data because the end
     * of the stream has been reached.
     * @throws NullPointerException      If <code>b</code> is <code>null</code>.
     * @throws IndexOutOfBoundsException If <code>off</code> is negative,
     *                                   <code>len</code> is negative, or <code>len</code> is greater than
     *                                   <code>b.length - off</code>
     * @throws IOException               if the first byte cannot be read for any reason
     *                                   other than end of file, the stream has been closed and the underlying
     *                                   input stream does not support reading after close, or another I/O
     *                                   error occurs.
     * @throws SocketTimeoutException if the socket timed out
     * @see java.io.FilterInputStream#in
     * @see java.io.InputStream#read(byte[], int, int)
     */
    int read(byte[] buffer, int byteOffset, int byteCount) throws SocketTimeoutException, IOException;

    /**
     * See the general contract of the <code>readFully</code>
     * method of <code>DataInput</code>.
     *
     * Bytes
     * for this operation are read from the contained
     * input stream.
     *
     * @param buffer the buffer into which the data is read.
     * @throws EOFException if this input stream reaches the end before
     *                      reading all the bytes.
     * @throws IOException  the stream has been closed and the contained
     *                      input stream does not support reading after close, or
     *                      another I/O error occurs.
     * @throws SocketTimeoutException if the socket timed out
     * @see java.io.FilterInputStream#in
     */
    void readFully(byte[] buffer) throws SocketTimeoutException, IOException;

    /**
     * See the general contract of the <code>readFully</code>
     * method of <code>DataInput</code>.
     *
     * Bytes
     * for this operation are read from the contained
     * input stream.
     *
     * @param buffer    the buffer into which the data is read.
     * @param offset    the start offset of the data.
     * @param byteCount the number of bytes to read.
     * @throws EOFException if this input stream reaches the end before
     *                      reading all the bytes.
     * @throws IOException  the stream has been closed and the contained
     *                      input stream does not support reading after close, or
     *                      another I/O error occurs.
     * @throws SocketTimeoutException if the socket timed out
     * @see java.io.FilterInputStream#in
     */
    void readFully(byte[] buffer, int offset, int byteCount) throws SocketTimeoutException, IOException;

}
