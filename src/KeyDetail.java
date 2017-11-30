/**
 * Created by KK48481 on 22-09-2017.
 */
public class KeyDetail {
    private String tableName, columnName, constraintName, referencedTableName, referencedColumnName, ordinalPosition, characterMaxLength;
    boolean hasCollation;

    public KeyDetail(String tableName, String columnName, String constraintName, String referencedTableName, String referencedColumnName, String ordinalPosition, String characterMaxLength, boolean hasCollation) {
        this.tableName = tableName;
        this.columnName = columnName;
        this.constraintName = constraintName;
        this.referencedTableName = referencedTableName;
        this.referencedColumnName = referencedColumnName;
        this.ordinalPosition = ordinalPosition;
        this.characterMaxLength = characterMaxLength;
        this.hasCollation = hasCollation;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getConstraintName() {
        return constraintName;
    }

    public void setConstraintName(String constraintName) {
        this.constraintName = constraintName;
    }

    public String getReferencedTableName() {
        return referencedTableName;
    }

    public void setReferencedTableName(String referencedTableName) {
        this.referencedTableName = referencedTableName;
    }

    public String getReferencedColumnName() {
        return referencedColumnName;
    }

    public void setReferencedColumnName(String referencedColumnName) {
        this.referencedColumnName = referencedColumnName;
    }

    public String getOrdinalPosition() {
        return ordinalPosition;
    }

    public void setOrdinalPosition(String ordinalPosition) {
        this.ordinalPosition = ordinalPosition;
    }

    public String getCharacterMaxLength() {
        return characterMaxLength;
    }

    public void setCharacterMaxLength(String characterMaxLength) {
        this.characterMaxLength = characterMaxLength;
    }

    public boolean isHasCollation() {
        return hasCollation;
    }

    public void setHasCollation(boolean hasCollation) {
        this.hasCollation = hasCollation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        KeyDetail keyDetail = (KeyDetail) o;

        if (isHasCollation() != keyDetail.isHasCollation()) return false;
        if (getTableName() != null ? !getTableName().equals(keyDetail.getTableName()) : keyDetail.getTableName() != null)
            return false;
        if (getColumnName() != null ? !getColumnName().equals(keyDetail.getColumnName()) : keyDetail.getColumnName() != null)
            return false;
        if (getConstraintName() != null ? !getConstraintName().equals(keyDetail.getConstraintName()) : keyDetail.getConstraintName() != null)
            return false;
        if (getReferencedTableName() != null ? !getReferencedTableName().equals(keyDetail.getReferencedTableName()) : keyDetail.getReferencedTableName() != null)
            return false;
        if (getReferencedColumnName() != null ? !getReferencedColumnName().equals(keyDetail.getReferencedColumnName()) : keyDetail.getReferencedColumnName() != null)
            return false;
        if (getOrdinalPosition() != null ? !getOrdinalPosition().equals(keyDetail.getOrdinalPosition()) : keyDetail.getOrdinalPosition() != null)
            return false;
        return !(getCharacterMaxLength() != null ? !getCharacterMaxLength().equals(keyDetail.getCharacterMaxLength()) : keyDetail.getCharacterMaxLength() != null);

    }

    @Override
    public int hashCode() {
        int result = getTableName() != null ? getTableName().hashCode() : 0;
        result = 31 * result + (getColumnName() != null ? getColumnName().hashCode() : 0);
        result = 31 * result + (getConstraintName() != null ? getConstraintName().hashCode() : 0);
        result = 31 * result + (getReferencedTableName() != null ? getReferencedTableName().hashCode() : 0);
        result = 31 * result + (getReferencedColumnName() != null ? getReferencedColumnName().hashCode() : 0);
        result = 31 * result + (getOrdinalPosition() != null ? getOrdinalPosition().hashCode() : 0);
        result = 31 * result + (getCharacterMaxLength() != null ? getCharacterMaxLength().hashCode() : 0);
        result = 31 * result + (isHasCollation() ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "KeyDetail{" +
                "tableName='" + tableName + '\'' +
                ", columnName='" + columnName + '\'' +
                ", constraintName='" + constraintName + '\'' +
                ", referencedTableName='" + referencedTableName + '\'' +
                ", referencedColumnName='" + referencedColumnName + '\'' +
                ", ordinalPosition='" + ordinalPosition + '\'' +
                ", characterMaxLength='" + characterMaxLength + '\'' +
                ", hasCollation=" + hasCollation +
                '}';
    }
}
