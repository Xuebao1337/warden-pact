package dev.witherpact;

/** Version-independent bounds used by the server and boundary tests. */
public final class PactRules {
    private PactRules() {}
    public static int lifetimeTicks(int seconds,int level) { return (seconds+15*Math.max(0,Math.min(3,level)))*20; }
    public static int cooldownTicks(int seconds,int level) { return Math.max(1,seconds-3*Math.max(0,Math.min(3,level)))*20; }
    public static float guard(float damage,int totalLevels) { return damage*(1.0F-Math.min(.4F,Math.max(0,totalLevels)*.02F)); }
    public static double[] flight(float yaw,float strafe,float forward,double speed) {
        double length=Math.sqrt(strafe*strafe+forward*forward);
        if(length>1) { strafe/=length; forward/=length; }
        double angle=Math.toRadians(yaw);
        return new double[]{(strafe*Math.cos(angle)-forward*Math.sin(angle))*speed,
                            (forward*Math.cos(angle)+strafe*Math.sin(angle))*speed};
    }
}
