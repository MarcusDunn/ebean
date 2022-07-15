package io.ebeaninternal.dbmigration.model;

import io.ebeaninternal.dbmigration.ddlgeneration.platform.DdlHelp;
import io.ebeaninternal.dbmigration.migration.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class MTableTest {

  static MTable base() {
    MTable table = new MTable("tab");
    table.addColumn(new MColumn("id", "bigint"));
    table.addColumn(new MColumn("name", "varchar(20)"));
    table.addColumn(new MColumn("status", "varchar(3)"));
    return table;
  }

  static MTable newTable() {
    MTable table = new MTable("tab");
    table.addColumn(new MColumn("id", "bigint"));
    table.addColumn(new MColumn("name", "varchar(20)"));
    table.addColumn(new MColumn("comment", "varchar(1000)"));
    return table;
  }

  static MTable newTableAdd2Columns() {
    MTable table = new MTable("tab");
    table.addColumn(new MColumn("id", "bigint"));
    table.addColumn(new MColumn("name", "varchar(20)"));
    table.addColumn(new MColumn("status", "varchar(3)"));
    table.addColumn(new MColumn("comment", "varchar(1000)"));
    table.addColumn(new MColumn("note", "varchar(2000)"));
    return table;
  }

  static MTable newTableModifiedColumn() {
    MColumn modCol = new MColumn("name", "varchar(30)");// modified type
    modCol.setNotnull(true);

    MTable table = new MTable("tab");
    table.addColumn(modCol);
    table.addColumn(new MColumn("id", "bigint"));
    table.addColumn(new MColumn("status", "varchar(3)"));
    return table;
  }

  @Test
  void schema() {
    MTable table = new MTable("tab");
    assertNull(table.getSchema());

    table = new MTable("foo.tab");
    assertEquals("foo", table.getSchema());
    assertEquals("foo.tab", table.getName());
  }

  @Test
  void addColumnScalar_when_new() {
    MTable table = new MTable("tab");

    MColumn mColumn = table.addColumnScalar("billing_id", "bigint");
    assertThat(mColumn).isNotNull();
    assertThat(mColumn.getName()).isEqualTo("billing_id");
    assertThat(mColumn.getType()).isEqualTo("bigint");
  }

  @Test
  void addColumnScalar_when_existingColumnDefined() {
    MTable table = new MTable("tab");
    MColumn col = new MColumn("billing_id", "bigint");
    col.setForeignKeyName("fk_tab_billing_id");
    col.setForeignKeyIndex("ix_tab_billing_id");
    table.addColumn(col);

    MColumn mColumn = table.addColumnScalar("billing_id", "bigint");
    assertThat(mColumn).isSameAs(col);
  }

  @Test
  void test_allHistoryColumns() {
    MTable base = base();
    base.registerPendingDropColumn("fullName");
    base.registerPendingDropColumn("last");

    assertThat(base.allHistoryColumns(false)).containsExactly("id", "name", "status");
    assertThat(base.allHistoryColumns(true)).containsExactly("id", "name", "status", "fullName", "last");
  }

  @Test
  void test_dropTable() {
    MTable base = base();
    DropTable dropTable = base.dropTable();
    assertThat(dropTable.getName()).isEqualTo(base.getName());
  }

  @Test
  void test_compare_addColumnDropColumn() {
    ModelDiff diff = new ModelDiff();
    diff.compareTables(base(), newTable());

    List<Object> createChanges = diff.getApplyChanges();
    assertThat(createChanges).hasSize(1);
    AddColumn addColumn = (AddColumn) createChanges.get(0);
    assertThat(addColumn.getColumn()).extracting("name").contains("comment");
    assertThat(addColumn.getColumn()).extracting("type").contains("varchar(1000)");

    List<Object> dropChanges = diff.getDropChanges();
    assertThat(dropChanges).hasSize(1);

    DropColumn dropColumn = (DropColumn) dropChanges.get(0);
    assertThat(dropColumn.getColumnName()).isEqualTo("status");
    assertThat(dropColumn.getTableName()).isEqualTo("tab");
  }

  @Test
  void test_compare_addTwoColumnsToSameTable() {
    ModelDiff diff = new ModelDiff();
    diff.compareTables(base(), newTableAdd2Columns());

    List<Object> createChanges = diff.getApplyChanges();
    assertThat(createChanges).hasSize(1);

    AddColumn addColumn = (AddColumn) createChanges.get(0);
    assertThat(addColumn.getColumn()).extracting("name").contains("comment", "note");
    assertThat(addColumn.getColumn()).extracting("type").contains("varchar(1000)", "varchar(2000)");

    assertThat(diff.getDropChanges()).hasSize(0);
  }

  @Test
  void test_compare_modifyColumn() {
    ModelDiff diff = new ModelDiff();
    diff.compareTables(base(), newTableModifiedColumn());

    List<Object> createChanges = diff.getApplyChanges();
    assertThat(createChanges).hasSize(1);

    AlterColumn alterColumn = (AlterColumn) createChanges.get(0);
    assertThat(alterColumn.getColumnName()).isEqualTo("name");
    assertThat(alterColumn.getType()).isEqualTo("varchar(30)");
    assertThat(alterColumn.isNotnull()).isEqualTo(true);
    assertThat(alterColumn.getUnique()).isNull();
    assertThat(alterColumn.getCheckConstraint()).isNull();
    assertThat(alterColumn.getReferences()).isNull();

    assertThat(diff.getDropChanges()).hasSize(0);
  }

  @Test
  void test_apply_dropColumn() {
    MTable base = base();

    DropColumn dropColumn = new DropColumn();
    dropColumn.setTableName("tab");
    dropColumn.setColumnName("name");

    base.apply(dropColumn);
    assertThat(base.getColumn("name")).isNull();
  }

  @Test
  void test_apply_dropColumn_doesNotExist() {
    MTable base = base();

    DropColumn dropColumn = new DropColumn();
    dropColumn.setTableName(base.getName());
    dropColumn.setColumnName("DoesNotExist");
    assertThrows(IllegalStateException.class, () -> base.apply(dropColumn));
  }

  @Test
  void test_apply_alterColumn_doesNotExist() {
    MTable base = base();

    AlterColumn alterColumn = new AlterColumn();
    alterColumn.setTableName(base.getName());
    alterColumn.setColumnName("DoesNotExist");
    alterColumn.setType("integer");

    assertThrows(IllegalStateException.class, () ->  base.apply(alterColumn));
  }

  @Test
  void test_apply_alterColumn_type() {
    MTable base = base();

    AlterColumn alterColumn = new AlterColumn();
    alterColumn.setTableName(base.getName());
    alterColumn.setColumnName("id");
    alterColumn.setType("uuid");
    base.apply(alterColumn);

    assertThat(base.getColumn("id").getType()).isEqualTo("uuid");
  }

  @Test
  void test_compare_dropColumnWithForeignKey() {
    MTable base = base();
    MColumn fkColumn = new MColumn("customer_id", "bigint");
    fkColumn.setReferences("customer");
    fkColumn.setForeignKeyName("fk_tab_customer");
    fkColumn.setForeignKeyIndex("ix_tab_customer");
    base.addColumn(fkColumn);

    MTable baseWithoutFKeyColumn = base();

    ModelDiff diff = new ModelDiff();
    base.compare(diff, baseWithoutFKeyColumn);

    assertThat(diff.getDropChanges()).hasSize(2);
    AlterForeignKey dropFKey = (AlterForeignKey)diff.getDropChanges().get(0);
    assertThat(dropFKey.getTableName()).isEqualTo("tab");
    assertThat(dropFKey.getName()).isEqualTo("fk_tab_customer");
    assertThat(dropFKey.getColumnNames()).isEqualTo(DdlHelp.DROP_FOREIGN_KEY);

    DropColumn dropCol = (DropColumn)diff.getDropChanges().get(1);
    assertThat(dropCol.getTableName()).isEqualTo("tab");
    assertThat(dropCol.getColumnName()).isEqualTo("customer_id");
  }

  @Test
  void test_compare_addAndDropColumn() {
    MTable base = base();
    MTable newTable = newTable();

    ModelDiff diff = new ModelDiff();
    base.compare(diff, newTable);

    assertThat(diff.getApplyChanges()).hasSize(1);
    assertThat(diff.getDropChanges()).hasSize(1);
  }

  @Test
  void test_compare_addHistoryToTable() {
    MTable base = base();
    MTable withHistory = base();
    withHistory.setWithHistory(true);

    ModelDiff diff = new ModelDiff();
    base.compare(diff, withHistory);

    assertThat(diff.getDropChanges()).isEmpty();
    assertThat(diff.getApplyChanges()).hasSize(1);
    assertThat(diff.getApplyChanges().get(0)).isInstanceOf(AddHistoryTable.class);
  }

  @Test
  void test_compare_removeHistoryFromTable() {
    MTable withHistory = base();
    withHistory.setWithHistory(true);

    MTable noHistory = base();

    ModelDiff diff = new ModelDiff();
    withHistory.compare(diff, noHistory);

    assertThat(diff.getApplyChanges()).isEmpty();
    assertThat(diff.getDropChanges()).hasSize(1);
    assertThat(diff.getDropChanges().get(0)).isInstanceOf(DropHistoryTable.class);
  }

}
