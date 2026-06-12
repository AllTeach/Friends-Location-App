package com.example.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.example.data.Friend;
import java.util.ArrayList;
import java.util.List;

public class MapCustomView extends View {

    public interface OnFriendSelectedListener {
        void onFriendSelected(String friendId);
    }

    private OnFriendSelectedListener listener;

    // Map Center (SOMA SF reference)
    private final double centerLat = 37.7749;
    private final double centerLng = -122.4194;

    // Linear mapping scale (degrees to pixels)
    private final double scaleFactor = 120000.0; // zoom intensity baseline

    // Transformations
    private float mapScale = 1.0f;
    private float mapOffsetX = 0f;
    private float mapOffsetY = 0f;

    // Self Coordinates
    private double userLat = 37.7749;
    private double userLng = -122.4194;

    // Friend list & selection state
    private List<Friend> friends = new ArrayList<>();
    private String selectedFriendId = null;

    // Graphic paint definitions (Sophisticated Dark theme pairing)
    private Paint paintBg;
    private Paint paintGrid;
    private Paint paintWater;
    private Paint paintPark;
    private Paint paintRoad;
    private Paint paintRoadAccent;
    private Paint paintUserPin;
    private Paint paintFriendPin;
    private Paint paintFriendSelected;
    private Paint paintTextLabel;
    private Paint paintTextInitial;

    // Drag / gesture state variables
    private float lastTouchX;
    private float lastTouchY;
    private boolean isDragging = false;
    private float totalDragDist = 0f;

    public MapCustomView(Context context) {
        super(context);
        init();
    }

    public MapCustomView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MapCustomView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Background - #25232A
        paintBg = new Paint();
        paintBg.setColor(0xFF25232A);
        paintBg.setStyle(Paint.Style.FILL);

        // Fine gridlines - #49454F with alpha
        paintGrid = new Paint();
        paintGrid.setColor(0xFF49454F);
        paintGrid.setAlpha(60);
        paintGrid.setStyle(Paint.Style.STROKE);
        paintGrid.setStrokeWidth(2f);

        // Park - #1C1B1F
        paintPark = new Paint();
        paintPark.setColor(0xFF1C1B1F);
        paintPark.setStyle(Paint.Style.FILL);

        // Water - #381E72 with alpha
        paintWater = new Paint();
        paintWater.setColor(0xFF381E72);
        paintWater.setAlpha(120);
        paintWater.setStyle(Paint.Style.FILL);

        // Roads - #2B2930 / #49454F
        paintRoad = new Paint();
        paintRoad.setColor(0xFF2B2930);
        paintRoad.setStyle(Paint.Style.STROKE);
        paintRoad.setStrokeWidth(12f);
        paintRoad.setStrokeCap(Paint.Cap.ROUND);

        paintRoadAccent = new Paint();
        paintRoadAccent.setColor(0xFF49454F);
        paintRoadAccent.setStyle(Paint.Style.STROKE);
        paintRoadAccent.setStrokeWidth(4f);
        paintRoadAccent.setStrokeCap(Paint.Cap.ROUND);

        // Pins
        paintUserPin = new Paint();
        paintUserPin.setColor(0xFFD0BCFF); // M3 Purple
        paintUserPin.setStyle(Paint.Style.FILL);
        paintUserPin.setAntiAlias(true);

        paintFriendPin = new Paint();
        paintFriendPin.setColor(0xFFB1EEFF); // Sam's Cyan / Secondary key Theme
        paintFriendPin.setStyle(Paint.Style.FILL);
        paintFriendPin.setAntiAlias(true);

        paintFriendSelected = new Paint();
        paintFriendSelected.setColor(0xFFEADDFF); // High contrast selected glow
        paintFriendSelected.setStyle(Paint.Style.STROKE);
        paintFriendSelected.setStrokeWidth(6f);
        paintFriendSelected.setAntiAlias(true);

        // Typographies
        paintTextLabel = new Paint();
        paintTextLabel.setColor(0xFFE6E1E5); // High contrast surface text
        paintTextLabel.setTextSize(26f);
        paintTextLabel.setFakeBoldText(true);
        paintTextLabel.setAntiAlias(true);
        paintTextLabel.setTextAlign(Paint.Align.CENTER);

        paintTextInitial = new Paint();
        paintTextInitial.setColor(0xFF1C1B1F); // Dark contrast inside badge
        paintTextInitial.setTextSize(22f);
        paintTextInitial.setFakeBoldText(true);
        paintTextInitial.setAntiAlias(true);
        paintTextInitial.setTextAlign(Paint.Align.CENTER);
    }

    public void setOnFriendSelectedListener(OnFriendSelectedListener listener) {
        this.listener = listener;
    }

    public void setFriends(List<Friend> friends) {
        this.friends = friends;
        invalidate();
    }

    public void setUserLocation(double lat, double lng) {
        this.userLat = lat;
        this.userLng = lng;
        invalidate();
    }

    public void setSelectedFriendId(String selectedFriendId) {
        this.selectedFriendId = selectedFriendId;
        invalidate();
    }

    public void setMapScale(float scale) {
        this.mapScale = Math.max(0.3f, Math.min(scale, 4.0f));
        invalidate();
    }

    public float getMapScale() {
        return this.mapScale;
    }

    public void setMapOffsetX(float ox) {
        this.mapOffsetX = ox;
        invalidate();
    }

    public float getMapOffsetX() {
        return this.mapOffsetX;
    }

    public void setMapOffsetY(float oy) {
        this.mapOffsetY = oy;
        invalidate();
    }

    public float getMapOffsetY() {
        return this.mapOffsetY;
    }

    public void recenter() {
        this.mapOffsetX = 0f;
        this.mapOffsetY = 0f;
        this.mapScale = 1.0f;
        invalidate();
    }

    private float getScreenX(double lng) {
        double dx = (lng - centerLng) * scaleFactor + mapOffsetX;
        return (float) (getWidth() / 2.0 + dx * mapScale);
    }

    private float getScreenY(double lat) {
        double dy = -(lat - centerLat) * scaleFactor + mapOffsetY;
        return (float) (getHeight() / 2.0 + dy * mapScale);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 1. Draw solid dark background
        canvas.drawRect(0, 0, getWidth(), getHeight(), paintBg);

        // 2. Draw Parks (Yerba Buena, SOMA green area blocks)
        drawPark(canvas, 37.7818, -122.4045, 37.7798, -122.3995); // Park 1
        drawPark(canvas, 37.7735, -122.4215, 37.7715, -122.4170); // Park 2

        // 3. Draw Water reservior (simulating standard SOMA/Mission creek edge)
        drawWaterOnEast(canvas);

        // 4. Draw San Francisco Diagonal Grid Street system
        drawStreetRoads(canvas);

        // 5. Draw Fine mapping Grid coordinates lines
        drawMapGridlines(canvas);

        // 6. Draw User Location Pin
        float userX = getScreenX(userLng);
        float userY = getScreenY(userLat);

        // Draw pulsating halo
        Paint paintUserHalo = new Paint(paintUserPin);
        paintUserHalo.setAlpha(45);
        canvas.drawCircle(userX, userY, 28 * mapScale, paintUserHalo);
        canvas.drawCircle(userX, userY, 14 * mapScale, paintUserPin);

        // 7. Draw Friends
        for (Friend friend : friends) {
            float fx = getScreenX(friend.longitude);
            float fy = getScreenY(friend.latitude);
            boolean isSelected = friend.id.equals(selectedFriendId);

            // Draw outer selection aura
            if (isSelected) {
                canvas.drawCircle(fx, fy, 26 * mapScale, paintFriendSelected);
            }

            // Draw physical badge
            Paint pPin = new Paint(paintFriendPin);
            if (isSelected) {
                pPin.setColor(0xFFD0BCFF); // matching primary container highlighting
            }
            canvas.drawCircle(fx, fy, 16 * mapScale, pPin);

            // Draw initial label inside badge
            if (friend.name != null && !friend.name.isEmpty()) {
                String initial = friend.name.substring(0, 1).toUpperCase();
                paintTextInitial.setTextSize(14f * Math.max(0.6f, Math.min(mapScale, 1.8f)));
                // Center text vertically roughly
                canvas.drawText(initial, fx, fy + (5 * mapScale), paintTextInitial);
            }

            // Draw text labels below
            paintTextLabel.setTextSize(18f * Math.max(0.6f, Math.min(mapScale, 1.8f)));
            canvas.drawText(friend.name.split(" ")[0], fx, fy + (36 * mapScale), paintTextLabel);
        }
    }

    private void drawPark(Canvas canvas, double maxLat, double minLng, double minLat, double maxLng) {
        float left = getScreenX(minLng);
        float top = getScreenY(maxLat);
        float right = getScreenX(maxLng);
        float bottom = getScreenY(minLat);
        canvas.drawRect(left, top, right, bottom, paintPark);
    }

    private void drawWaterOnEast(Canvas canvas) {
        // Draw water block simulating East Bay edge starting at lng -122.385
        float left = getScreenX(-122.3850);
        float top = 0;
        float right = getWidth();
        float bottom = getHeight();
        if (left < getWidth()) {
            canvas.drawRect(left, top, right, bottom, paintWater);
        }
    }

    private void drawStreetRoads(Canvas canvas) {
        // Simulating 5 major parallel Streets and 4 crossing avenues in SOMA Sf grid system
        // Market Street, Mission Street, Howard Street, Folsom Street, Harrison Street
        double[][] roads = {
            // Latitudes represent streets
            {37.7850, -122.4300, 37.7850, -122.3850}, // Market St
            {37.7818, -122.4300, 37.7818, -122.3850}, // Mission St
            {37.7785, -122.4300, 37.7785, -122.3850}, // Howard St
            {37.7750, -122.4300, 37.7750, -122.3850}, // Folsom St
            {37.7715, -122.4300, 37.7715, -122.3850}, // Harrison St

            // Longitudes represent crossing avenues (8th St down to 3rd St)
            {37.7900, -122.4200, 37.7650, -122.4200}, // 8th St
            {37.7900, -122.4130, 37.7650, -122.4130}, // 7th St
            {37.7900, -122.4060, 37.7650, -122.4060}, // 6th St
            {37.7900, -122.3990, 37.7650, -122.3990}, // 5th St
            {37.7900, -122.3920, 37.7650, -122.3920}  // 4th St
        };

        paintRoad.setStrokeWidth(10f * mapScale);
        paintRoadAccent.setStrokeWidth(3f * mapScale);

        for (double[] road : roads) {
            float x1 = getScreenX(road[1]);
            float y1 = getScreenY(road[0]);
            float x2 = getScreenX(road[3]);
            float y2 = getScreenY(road[2]);

            canvas.drawLine(x1, y1, x2, y2, paintRoad);
            canvas.drawLine(x1, y1, x2, y2, paintRoadAccent);
        }
    }

    private void drawMapGridlines(Canvas canvas) {
        // Draw a light technical blueprint mesh coordinate layer
        int step = (int) (120 * mapScale);
        if (step > 30) {
            for (int x = 0; x < getWidth(); x += step) {
                canvas.drawLine(x, 0, x, getHeight(), paintGrid);
            }
            for (int y = 0; y < getHeight(); y += step) {
                canvas.drawLine(0, y, getWidth(), y, paintGrid);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                isDragging = false;
                totalDragDist = 0f;
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - lastTouchX;
                float dy = event.getY() - lastTouchY;
                mapOffsetX += dx / mapScale;
                mapOffsetY += dy / mapScale;
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                totalDragDist += Math.sqrt(dx * dx + dy * dy);
                if (totalDragDist > 10) {
                    isDragging = true;
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
                if (!isDragging) {
                    // This was a tap. Check if user tapped a friend's physical node.
                    checkFriendTap(event.getX(), event.getY());
                }
                isDragging = false;
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void checkFriendTap(float tx, float ty) {
        double minDistance = 50.0; // Touch search radius (pixels)
        Friend closestFriend = null;

        for (Friend friend : friends) {
            float fx = getScreenX(friend.longitude);
            float fy = getScreenY(friend.latitude);
            double dist = Math.sqrt((tx - fx) * (tx - fx) + (ty - fy) * (ty - fy));
            if (dist < minDistance) {
                minDistance = dist;
                closestFriend = friend;
            }
        }

        if (closestFriend != null && listener != null) {
            listener.onFriendSelected(closestFriend.id);
        }
    }
}
