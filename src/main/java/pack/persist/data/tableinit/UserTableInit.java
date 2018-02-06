package pack.persist.data.tableinit;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import pack.persist.data.Schema;
import pack.persist.data.User;

@DatabaseTable(tableName = Schema.TABLE_USERS)
public class UserTableInit {

    @DatabaseField(columnName = User.FIELD_USER_ID, generatedId = true)
    private int id;
}
