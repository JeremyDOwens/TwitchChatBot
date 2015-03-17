/**
 * TwitchChatBot is a class designed to abstract connection to Twitch chat by a bot,
 * allowing developers to focus on processing data instead of managing the connection.
 * @author Jeremy "Tsagh" Owens
 * @version 1.0 - 2 Feb 2015 
*/

package tv.tsagh.twitch;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;


public class TwitchChatBot implements AutoCloseable {
    private final String nick;
    private final String pw;
    private SocketChannel chan;
    private ByteBuffer buff;
    //CharsetEncoders instantiated and set to Replace malformed input
    private final CharsetEncoder enc = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPLACE);
    private final CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE);    
    private TwitchChatReader readMe;
    private boolean autoPingBack;
    private int clientNumber;
    
    
/** Primary constructor
 * @param nickIn the Twitch.tv username of the bot
 * @param pwIn The oauth key (not including "oauth:")
 * @param pingBackIn Option to have the bot automatically respond to pings.
 * @param clientIn Integer between 1 and 3, corresponding to the Twitch chat client you wish to use.
 */
    public TwitchChatBot(String nickIn, String pwIn, boolean pingBackIn, int clientIn) {
        if (clientIn < 0 || clientIn > 3) throw new IllegalArgumentException("Invalid client number: " + clientIn);
        nick = nickIn;
        pw = pwIn;
        autoPingBack = pingBackIn;
        clientNumber = 3;        
    }    
    
/** Overloaded constructor automatically sets autoPingBack to true    
 * @param nickIn the Twitch.tv username of the bot
 * @param pwIn The oauth key (not including "oauth:")
 */
    public TwitchChatBot(String nickIn, String pwIn) {
        this(nickIn, pwIn, true, 3);
    }
    
/** Completes the connection to the chat server    
 * @throws IOException If cannot connect
 */
    public void connect() throws IOException{
        if (chan != null) {
            chan.close();
            chan = null;
        }
        chan = SocketChannel.open();
        chan.configureBlocking(false);
        chan.connect(new InetSocketAddress("irc.twitch.tv", 6667));
        buff = ByteBuffer.allocate(200000);
        while (!chan.finishConnect()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException exp) {
                System.err.println(exp.getMessage());
                exp.printStackTrace(System.err);
            }
        }
        if (chan.isConnected()) {
            write("PASS oauth:" + pw + "\r\n");
            write("NICK " + nick + "\r\n");
            write("USER " + nick + "\r\n");
            write("TWITCHCLIENT " + clientNumber + "\r\n");
        } else throw new IOException("Failed to connect to the bot. Perhaps wrong credentials?");
    }
/** Return an iterable reader object for processing input
 * @return TwitchChatReader
 */
    public synchronized Iterable<String> getReader() {
        if (readMe == null) {
            readMe = new TwitchChatReader();
        }
        return readMe;
    }

/** Send a command to the chat server from the bot
 * @param input String containing the command sent to the server.
 * @throws IOException If unable to write
 */
    private void write(String input) throws IOException {
        chan.write(enc.encode(CharBuffer.wrap(input.toCharArray())));
    }
/** Send a message to a chat channel    
* @param msg The message to be sent to the server.
* @param channel The name of the channel you wish to send the message to.
* @throws IOException if unable to write
*/
    public void sendMsg(String msg, String channel) throws IOException {
        if (!isConnected()) throw new IOException("The bot is not connected to the server.");
        write("PRIVMSG #" + channel.toLowerCase() + " :" + msg + "\r\n");
    }    
/** Join a chat channel    
 * @param channel The name of the channel you wish to join.
 * @throws IOException if unable to write
 */
    public void join(String channel) throws IOException {
        if (!isConnected()) throw new IOException("The bot is not connected to the server.");
        write("JOIN #" + channel.toLowerCase() + "\r\n");
    }
/** Query the server to return a viewer list    
* @param channel The name of the channel you wish to see the viewer list of.
* @throws IOException if unable to write
*/
    public void who(String channel) throws IOException {
        if (!isConnected()) throw new IOException("The bot is not connected to the server.");
        write("WHO #" + channel.toLowerCase() + "\r\n");
    }    
/** Leave a chat channel    
 * @param channel the name of the channel you wish to leave
 * @throws IOException If unable to write
 */
    public void part(String channel) throws IOException {
        if (!isConnected()) throw new IOException("The bot is not connected to the server.");
        write("PART #" + channel.toLowerCase() + "\r\n");
    }
/** Respond to a ping    
* @param line The entire raw line from the PING sent to the channel.
* @throws IOException If unable to write
*/
    public void pong(String line) throws IOException {
        if (!isConnected()) throw new IOException("The bot is not connected to the server.");
        write(line.replace("PING", "PONG") + "\r\n");
    }
/** Check to ensure that the bot is connected before attempting to use the connection
 * @return true if bot is connected to the chat server
 */
    public boolean isConnected() {
        if (chan != null) return chan.isConnected();
        else return false;
    }

/** Closes all assets freeing up resources */
    @Override
    public void close() throws IOException {
        if (chan != null) chan.close();    
    }

/** Returns the username of the bot 
 * @return the username of the bot
 **/
    @Override
    public String toString() {
        return nick;
    }
    
/** Background code to read and decode information from the bytestream
 * If autoPingBack is enabled, there will be some extra processing allowing the
 * bot to respond to a ping.
 * @return Returns a multi-line string containing all information in the buffer.
 * @throws IOException
 */
    private synchronized String readLine() throws IOException{ // Read a line from input stream
        if (chan.isConnected()) {            
            if (chan.read(buff) <= 0) return null;
            if (!buff.hasRemaining()) return null;
        } else throw new IOException("Bot is not connected");
        buff.flip();
        String outputString = dec.decode(buff).toString();
        buff.clear();
        if (outputString.length() > 0) {
            if (autoPingBack) {
                String[] hiddenPingTest = outputString.split("\n");
                for (String str: hiddenPingTest) {
                    if (str.startsWith("PING ")) {                    
                        pong(str);
                    }
                }
            }
            return outputString;
        } else return null;        
    }
    
/** TwitchChatReader is a helper class to encapsulate reading information from
 * the bot for ease of processing. The iterable class maintains an ArrayList of the
 * input lines received from the bot's SocketChannel and allows the user to iterate
 * over them.
 * 
 * Suggested use is with the enhanced for loop.
 * 
 * @author Jeremy "Tsagh" Owens
 *
 */
    private class TwitchChatReader implements Iterable<String> {
        private ArrayList<String> arr;
        private TwitchChatReader() {
            arr = new ArrayList<String>();
        }
        @Override
        public Iterator<String> iterator() {
            return new TwitchChatIterator();
        }
        private class TwitchChatIterator implements Iterator<String> {        

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
            @Override
            public synchronized boolean hasNext() {
                if (arr.size() > 0) {
                    return true;
                }
                try {
                    String line = readLine();            
                    if (line != null) {
                        for (String x: line.split("\n")) {
                            arr.add(x);
                        }
                    }
                }
                catch (IOException exp) {
                    System.err.println(exp.getMessage());
                    exp.printStackTrace(System.err);
                }
                if (arr.size() > 0) {
                    return true;
                }
                else return false;
            }
            @Override
            public synchronized String next() {
                if (arr.size() == 0) throw new NoSuchElementException("No output to process");
                String retString = arr.get(0);
                arr.remove(0);
                return retString;
            }        
        }
    }
}
