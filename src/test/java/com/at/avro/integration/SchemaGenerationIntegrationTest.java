package com.at.avro.integration;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType.HSQL;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.function.Function;

import org.apache.commons.io.IOUtils;
import org.junit.*;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;

import com.at.avro.AvroSchema;
import com.at.avro.DbSchemaExtractor;
import com.at.avro.SchemaGenerator;
import com.at.avro.config.AvroConfig;
import com.at.avro.config.FormatterConfig;
import com.at.avro.formatters.SchemaFormatter;
import com.at.avro.mappers.RemovePlural;
import com.at.avro.mappers.ToCamelCase;

/**
 * @author artur@callfire.com
 */
public class SchemaGenerationIntegrationTest {

    private DbSchemaExtractor schemaExtractor = getDbSchemaExtractor();
    private AvroConfig avroConfig = new AvroConfig("test.namespace");

    @BeforeClass
    public static void initDb() {
        new EmbeddedDatabaseBuilder().setType(HSQL).setName("testdb").addScripts("create_test_tables.sql").build();
    }

    private DbSchemaExtractor getDbSchemaExtractor() {
        return new DbSchemaExtractor("jdbc:hsqldb:mem:testdb", "sa", "");
    }

    @Test
    public void testGenerationWithDefaultSettings() throws SQLException, IOException {
        AvroSchema avroSchema = schemaExtractor.getForTable(avroConfig, "public", "test_records");
        assertThat(SchemaGenerator.generate(avroSchema), is(getFileContent("/avro/default.avsc")));
    }

    @Test
    public void testGenerationWithAllNullableFields() throws SQLException, IOException {
        avroConfig.setNullableTrueByDefault(true);

        AvroSchema avroSchema = schemaExtractor.getForTable(avroConfig, "public", "test_records");
        assertThat(SchemaGenerator.generate(avroSchema), is(getFileContent("/avro/all_nullable.avsc")));
    }

    @Test
    public void testGenerationAllFieldsDefaultNull() throws SQLException, IOException {
        avroConfig.setAllFieldsDefaultNull(true);

        AvroSchema avroSchema = schemaExtractor.getForTable(avroConfig, "public", "test_records");
        assertThat(SchemaGenerator.generate(avroSchema), is(getFileContent("/avro/all_fields_default_null.avsc")));
    }

    @Test
    public void testSpecificFormatterConfig() throws SQLException, IOException {
        FormatterConfig formatterConfig = FormatterConfig.builder()
                .setIndent("    ")
                .setPrettyPrintFields(true)
                .build();

        AvroSchema avroSchema = schemaExtractor.getForTable(avroConfig, "public", "test_records");
        assertThat(SchemaGenerator.generate(avroSchema, formatterConfig), is(getFileContent("/avro/pretty_print_bigger_indent.avsc")));
    }

    @Test
    public void testNonPrettyPrint() throws SQLException, IOException {
        FormatterConfig formatterConfig = FormatterConfig.builder()
                .setPrettyPrintSchema(false)
                .build();

        AvroSchema avroSchema = schemaExtractor.getForTable(avroConfig, "public", "test_records");
        assertThat(SchemaGenerator.generate(avroSchema, formatterConfig), is(getFileContent("/avro/non_pretty_print.avsc")));
    }

    @Test
    public void testNameMappers() throws SQLException, IOException {
        Function<String, String> nameMapper = new ToCamelCase().andThen(new RemovePlural());
        avroConfig.setSchemaNameMapper(nameMapper).setFieldNameMapper(nameMapper);

        AvroSchema avroSchema = schemaExtractor.getForTable(avroConfig, "public", "test_records");
        assertThat(SchemaGenerator.generate(avroSchema), is(getFileContent("/avro/camel_case.avsc")));
    }

    @Test
    public void testOverrideFormatter() throws Exception {
        FormatterConfig formatterConfig = FormatterConfig.builder()
                .setFormatter(AvroSchema.class, new SchemaFormatter() {
                    @Override
                    public String toJson(AvroSchema avroSchema, FormatterConfig config) {
                        return super.toJson(avroSchema, config).replaceAll(" ", "").replaceAll("\n", "");
                    }
                })
                .build();

        AvroSchema avroSchema = schemaExtractor.getForTable(avroConfig, "public", "test_records");
        assertThat(SchemaGenerator.generate(avroSchema, formatterConfig), is(getFileContent("/avro/custom_formatter.avsc")));
    }

    @Test
    public void testCustomAddingProperties() throws Exception {
        avroConfig.setAvroSchemaPostProcessor(((avroSchema, table) -> {
            avroSchema.addCustomProperty("test-propertyy", "test-value");
        }));

        AvroSchema avroSchema = schemaExtractor.getForTable(avroConfig, "public", "test_records");
        assertThat(SchemaGenerator.generate(avroSchema), is(getFileContent("/avro/custom_property.avsc")));
    }

    @Test
    public void testGetAllAvroSchemas() throws Exception {
        List<AvroSchema> result = schemaExtractor.getAll(avroConfig);
        assertThat(result.size(), is(1));
    }

    @Test
    public void testGetAvroSchemasInExistingDbSchema() throws Exception {
        List<AvroSchema> result = schemaExtractor.getForSchema(avroConfig, "public");
        assertThat(result.size(), is(1));
    }

    @Test(expected = RuntimeException.class)
    public void testGetAvroSchemasInMissingDbSchema() throws Exception {
        schemaExtractor.getForSchema(avroConfig, "nonexisting");
    }

    @Test
    public void testGetAvroSchemasForTables() throws Exception {
        List<AvroSchema> result = schemaExtractor.getForTables(avroConfig, "public", "test_records", "non_existing");
        assertThat(result.size(), is(1));
    }

    @Test
    public void testToString() {
        AvroSchema avroSchema = schemaExtractor.getForTable(avroConfig, "public", "test_records");
        assertThat(avroSchema.toString(), is(
            "AvroSchema[" +
                "name='test_records', namespace='test.namespace', " +
                "fields=[" +
                    "AvroField[name='id', type=AvroType[type=Primitive(int), nullable=false]], " +
                    "AvroField[name='name', type=AvroType[type=Primitive(string), nullable=true]], " +
                    "AvroField[name='created', type=AvroType[type=Date(long): timestamp-millis, nullable=false]], " +
                    "AvroField[name='updated', type=AvroType[type=Date(long): timestamp-millis, nullable=true]], " +
                    "AvroField[name='decimal_field', type=AvroType[type=Decimal(string): decimal[20:3], nullable=true]], " +
                    "AvroField[name='other_decimal_field', type=AvroType[type=Decimal(string): decimal[128:0], nullable=true]]" +
                "], " +
                "customProperties={}" +
             "]"
        ));
    }

    private String getFileContent(String fileName) throws IOException {
        return IOUtils.toString(SchemaGenerationIntegrationTest.class.getResourceAsStream(fileName), "UTF-8");
    }
}
