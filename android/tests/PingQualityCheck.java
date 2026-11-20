package ru.mtproxy.picker;
public final class PingQualityCheck {
 public static void main(String[] args){long[] values={-2,-1,0,99,100,249,250,499,500,999,1000,2000};int[] bands={-1,5,0,0,1,1,2,2,3,3,4,4};for(int i=0;i<values.length;i++){if(PingQuality.band(values[i])!=bands[i])throw new AssertionError("Threshold "+values[i]);if(PingQuality.label(values[i]).isEmpty())throw new AssertionError("Label");}if(PingQuality.color(-1)==PingQuality.color(50)||!PingQuality.label(-1).equals("Помер")||!PingQuality.label(-2).equals("Не проверен"))throw new AssertionError("Offline / unchecked distinction");System.out.println("PASS: all ping thresholds, labels and offline/unchecked distinction");}
}
