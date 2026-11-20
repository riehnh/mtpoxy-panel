package ru.mtproxy.picker;
/** Shared ping labels for list rows and the candidate screen. */
final class PingQuality {
 static int band(long ms){return ms==-2?-1:ms<0?5:ms<100?0:ms<250?1:ms<500?2:ms<1000?3:4;}
 static String label(long ms){int b=band(ms);return b<0?"Не проверен":new String[]{"Лучший","Норм","Сомнительно","Кал","Бяда","Помер"}[b];}
 static int color(long ms){int b=band(ms);return b<0?0xffdedede:new int[]{0xff63d28a,0xffa7da70,0xffffda64,0xffffa351,0xffff795f,0xffff5252}[b];}
}
