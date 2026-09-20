from "allthelogs" import allEntries, ChatEntry;

allEntries().forEach(entry: ChatEntry -> {
    console.log(entry.message);
    if (entry.chatLog.minecraftVersion == "26.2") {
        writeToOutputFile(entry.timestamp + ": " + entry.message);
    }
})
