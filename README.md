# irc-indexer

An IRC logger bot that indexes its logs in an [elasticsearch](http://elasticsearch.org) index.

A web app for viewing/searching the logs is in progress.

## Usage

1. Install and start elasticsearch
2. Checkout source code (you'll need leiningen or cake to build it)
3. Modify `config` (note that you can specify an alternate config file on the command line); you'll at least need to set the IRC server name and channels to log, and probably want an announcement and help text with URLs to where the index can be viewed.
4. Create the index: `lein run --setup`
5. Start logging: `lein run --log`

## Known Issues

- Problems if the bot is assigned a different nick than you specify in the config file (could happen if you specify too long a nick)
- Needs a way to gracefully shut down, so pending updates will be flushed to the index

## License

Copyright (C) 2011 Jacob Williams

Distributed under the Eclipse Public License, the same as Clojure.
