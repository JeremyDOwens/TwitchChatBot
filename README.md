# TwitchChatBot
Simple Java API class to abstract connection to Twitch Chat, for use in chat bot applications

When developing an application using TwitchChatBot

Create an Instance of TwitchChatBot using the login information with the Twitch account you wish the application to control. Use
of the try-with-resources construct is supported to ensure closing of the bot when necessary.

Call connect() to connect the bot - it is recommended to wait and/or check to ensure success before continuing

Various types of messages supported by Twitch are supported through methods currently, with a few more like "CLEARCHAT" to be
added soon.

Reading information from the server is done through the use of an Iterable<String> accessed by calling getReader(). This allows
the application to use the enhanced for loop to step through chat lines and process them individually. Alternatively, an
application may step through manually. A new reader object is not instantiated with each individual call to getReader().
Each instance of TwitchChatBot only ever has one reader object associated with it. This gives the option of parsing chat using:
  for(String line : botName.getReader())
  {
    doStuffWithString();
  }

It may, depending on how you use it, be beneficial to separate input and output into separate threads, but it is designed to be
non-blocking. More testing to come on that.
