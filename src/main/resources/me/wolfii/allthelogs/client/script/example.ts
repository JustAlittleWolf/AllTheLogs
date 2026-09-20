/**
 * AllTheLogs scripts are JavaScript (GraalJS). Do not import anything: these names are globals.
 * Zero-argument getters are properties (`entry.message`). Methods that take arguments are functions
 * (`ChatQuery.all().withVersion("26.2")`). Dates and times are ISO-8601 strings.
 *
 * ```ts
 * declare function allEntries(): ChatEntry[];
 * declare function writeToOutputFile(text: string): void;
 * declare const database: LogDatabase;
 * declare const ChatQuery: ChatQueryCtor;
 * declare const Sort: { readonly ASCENDING: Sort; readonly DESCENDING: Sort };
 *
 * interface ChatQueryCtor {
 *   all(): ChatQuery;
 *   readonly Sort: typeof Sort;
 * }
 *
 * type Sort = "ASCENDING" | "DESCENDING";
 *
 * interface ChatQuery {
 *   readonly substring: string | null;
 *   readonly caseSensitive: boolean;
 *   readonly regex: string | null;
 *   readonly version: string | null;
 *   readonly serverOrWorld: string | null;
 *   readonly startingAt: string | null;
 *   readonly upUntil: string | null;
 *   readonly contextLines: number;
 *   readonly limit: number;
 *   readonly sort: Sort;
 *   readonly offset: string | null;
 *   readonly offsetSource: LogSource | null;
 *   readonly offsetLine: number;
 *   readonly skip: number;
 *   readonly hasTextFilter: boolean;
 *   withSubstring(substring: string): ChatQuery;
 *   withSubstringCaseSensitive(substring: string): ChatQuery;
 *   withRegex(regex: string): ChatQuery;
 *   withVersion(version: string): ChatQuery;
 *   withServerOrWorld(serverOrWorld: string): ChatQuery;
 *   startingAt(startingAt: string): ChatQuery;
 *   upUntil(upUntil: string): ChatQuery;
 *   withContextLines(contextLines: number): ChatQuery;
 *   withLimit(limit: number): ChatQuery;
 *   withSort(sort: Sort): ChatQuery;
 *   withOffset(offset: string): ChatQuery;
 *   withOffset(offset: string, source: LogSource, lineIndex: number): ChatQuery;
 *   withSkip(skip: number): ChatQuery;
 * }
 *
 * interface LogDatabase {
 *   readonly isOpen: boolean;
 *   readonly allEntries: ChatEntry[];
 *   readonly chatLogs: ChatLog[];
 *   readonly metadata: LogStoreMetadata;
 *   readonly databasePath: string | null;
 *   findEntries(query: ChatQuery): ChatEntry[];
 *   summarizeMatches(query: ChatQuery): MatchSummary;
 *   countMatches(query: ChatQuery): number;
 *   entriesAround(log: ChatLog, lineIndex: number, radius: number): ChatEntry[];
 *   entriesAround(log: ChatLog, lineIndex: number, before: number, after: number): ChatEntry[];
 * }
 *
 * interface ChatEntry {
 *   readonly chatLog: ChatLog;
 *   readonly timestamp: string;
 *   readonly lineIndex: number;
 *   readonly message: string;
 *   readonly formatting: number[] | null;
 *   readonly minecraftUser: string | null;
 *   readonly serverOrWorld: string | null;
 * }
 *
 * interface ChatLog {
 *   readonly source: LogSource;
 *   readonly date: string;
 *   readonly minecraftVersion: string;
 *   readonly startTime: string;
 *   readonly endTime: string;
 *   readonly minecraftUser: string | null;
 * }
 *
 * type LogSource = LogSourceFile | LogSourceArchive | LogSourceSession;
 *
 * interface LogSourceFile {
 *   readonly path: string;
 * }
 *
 * interface LogSourceArchive {
 *   readonly path: string;
 *   readonly entryPath: string;
 * }
 *
 * interface LogSourceSession {
 *   readonly id: string | null;
 * }
 *
 * interface LogStoreMetadata {
 *   readonly minecraftVersions: string[];
 *   readonly serverOrWorlds: string[];
 *   readonly firstLogDate: string | null;
 *   readonly lastLogDate: string | null;
 *   readonly chatLogCount: number;
 *   readonly chatEntryCount: number;
 *   readonly databaseSizeBytes: number;
 * }
 *
 * interface MatchSummary {
 *   readonly oldest: string | null;
 *   readonly newest: string | null;
 *   readonly matches: number;
 *   readonly days: MatchDay[];
 *   readonly uniqueDates: number;
 *   readonly dates: string[];
 * }
 *
 * interface MatchDay {
 *   readonly date: string;
 *   readonly oldest: string;
 *   readonly newest: string;
 *   readonly matches: number;
 *   readonly collapsed: boolean;
 * }
 * ```
 */

const stats = database.metadata;
console.log(stats.chatLogCount + " logs, " + stats.chatEntryCount + " entries");

const query = ChatQuery.all()
    .withRegex("(?i)\\bwelcome\\b")
    .withSort(Sort.DESCENDING)
    .withLimit(50);

const summary = database.summarizeMatches(query);
console.log(summary.matches + " messages matching /welcome/i (writing up to 50 newest)");

database.findEntries(query).forEach((entry) => {
    writeToOutputFile(entry.timestamp + " [" + entry.chatLog.minecraftVersion + "] " + entry.message);
});
