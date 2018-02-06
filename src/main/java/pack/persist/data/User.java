package pack.persist.data;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.Objects;

@DatabaseTable(tableName = Schema.TABLE_USERS)
public class User {

    public static final String FIELD_USER_ID = "id";
    public static final String FIELD_GOOGLE_USER_ID = "google_user_id";

    @DatabaseField(columnName = FIELD_USER_ID, generatedId = true)
    private int id;

    @DatabaseField(columnName = FIELD_GOOGLE_USER_ID, canBeNull = false)
    private String googleUserId;


    @Override
    public int hashCode() {
        return Objects.hash(id, googleUserId);
    }

    @Override
    public boolean equals(Object other) { // Do not compare database primary key
        // Unclear if we depend on these at all
        throw new RuntimeException("Not Implemented");
    }

    //////////////////////////////////

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getGoogleUserId() {
        return googleUserId;
    }

    public void setGoogleUserId(String userId) {
        this.googleUserId = userId;
    }
}
