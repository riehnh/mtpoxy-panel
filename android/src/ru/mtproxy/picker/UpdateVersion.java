package ru.mtproxy.picker;
/** Android versionCode comparison, independent of display version strings. */
final class UpdateVersion {static boolean shouldCheck(boolean automatic,boolean manual){return automatic||manual;}static boolean newer(int installed,int available){return installed>0&&available>installed;}}
