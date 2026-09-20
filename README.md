# All The Logs

**Search all of your minecraft message history across sessions, using the stored logs!**

Minecraft stores logs from each play session by default. These logs contain information about errors, but also all received chat messages. This mod indexes these log files and presents an intuitive interface for searching through your logs. Your messages never leave your system!

Available on [Modrinth](https://modrinth.com/mod/allthelogs).

## Dependencies

- [owo-lib](https://modrinth.com/mod/owo-lib) is **required**
- [YetAnotherConfigLib](https://modrinth.com/mod/yacl) is **required**
- [Fabric API](https://modrinth.com/mod/fabric-api) is **required**
- [Mod Menu](https://modrinth.com/mod/modmenu) is **suggested**

## Searching your logs

The log browser can be accessed by using the "AllTheLogs" button in the main menu, in the pause menu, or by using the client side command `/allthelogs gui`. From there you can use the search bar to find anything in your logs. Context lines allow you to see nearby messages, filtering (including by server or world) and regex are supported as well.

![Example gif of the search screen at work](https://cdn.modrinth.com/data/cached_images/c75a2a9c5366cfea4a81c988620b70fb5a0bf99b.gif)


## Importing logs

The mod will import all logs of your current instance when the game first launches, from then on it records messages while the game is running. Extra instance folders can be auto-imported on launch from settings (`/allthelogs settings` or Mod Menu). If you have logs stored in another directory, you can use the import function to also add those messages to the database. There are presets for common log locations, but logs can also be imported from any directory, even from an archive!

![Example gif of the import at work](https://cdn.modrinth.com/data/cached_images/b60b230f16cb099db113cccbf3426b45ebd3e823.gif)


## Scripts

`/allthelogs scripts` runs JavaScript against your local log database. The script engine is downloaded the first time you open that screen.

## Common Issues

**My messages aren't showing up!!!!**

First, check if the logs are in the folder of your current instance, if not you will need to import them. If you have deleted some of your log files previously, there is also no way of restoring those messages, unless you are able to restore the log files. Minecraft also has a bug, [MC-100524](https://bugs.mojang.com/browse/MC/issues/MC-100524), that overwrites the logs of previous sessions when launching the game more than 7 times a day.


**Failed to load DuckDB?**

On first start the mod downloads the [DuckDB](https://duckdb.org/) driver which is used to store your messages, so they can be searched really fast. This means an internet connection is required, however only for the first boot.


## XY is not working

Please report issues on github https://github.com/JustAlittleWolf/AllTheLogs/issues
