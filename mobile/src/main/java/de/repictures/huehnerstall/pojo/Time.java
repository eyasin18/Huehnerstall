package de.repictures.huehnerstall.pojo;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Time {

    public int hour;
    public int minutes;

    public Time() {
        // Default constructor required for calls to DataSnapshot.getValue(User.class)
    }

    public Time(int hour, int minutes) {
        this.hour = hour;
        this.minutes = minutes;
    }
}