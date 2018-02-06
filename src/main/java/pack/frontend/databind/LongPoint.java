package pack.frontend.databind;

public class LongPoint {
    private long x;
    private int y;

    public LongPoint(long x, int y) {
        this.x = x;
        this.y = y;
    }

    public boolean hasSameX(LongPoint other) {
        boolean isSame = this.getX() == other.getX();
        return isSame;
    }

    public boolean hasSameY(LongPoint other) {
        boolean isSame = this.getY() == other.getY();
        return isSame;
    }

    public long getX() {
        return x;
    }

    public void setX(long x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }
}
