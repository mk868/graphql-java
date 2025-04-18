package graphql.schema.idl;

import graphql.PublicApi;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import graphql.schema.idl.errors.StrictModeWiringException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;

import static graphql.Assert.assertNotNull;
import static java.lang.String.format;

/**
 * A type runtime wiring is a specification of the data fetchers and possible type resolver for a given type name.
 * <br>
 * This is used by {@link RuntimeWiring} to wire together a functional {@link GraphQLSchema}
 */
@PublicApi
public class TypeRuntimeWiring {

    private final static AtomicBoolean DEFAULT_STRICT_MODE = new AtomicBoolean(true);

    /**
     * By default {@link TypeRuntimeWiring} builders are in strict mode, but you can set a JVM wide value too
     *
     * @param strictMode the desired strict mode state
     *
     * @see Builder#strictMode(boolean)
     */
    public static void setStrictModeJvmWide(boolean strictMode) {
        DEFAULT_STRICT_MODE.set(strictMode);
    }

    /**
     * @return the current JVM wide state of strict mode
     */
    public static boolean getStrictModeJvmWide() {
        return DEFAULT_STRICT_MODE.get();
    }

    private final String typeName;
    private final DataFetcher defaultDataFetcher;
    private final Map<String, DataFetcher> fieldDataFetchers;
    private final TypeResolver typeResolver;
    private final EnumValuesProvider enumValuesProvider;

    private TypeRuntimeWiring(String typeName, DataFetcher defaultDataFetcher, Map<String, DataFetcher> fieldDataFetchers, TypeResolver typeResolver, EnumValuesProvider enumValuesProvider) {
        this.typeName = typeName;
        this.defaultDataFetcher = defaultDataFetcher;
        this.fieldDataFetchers = fieldDataFetchers;
        this.typeResolver = typeResolver;
        this.enumValuesProvider = enumValuesProvider;
    }

    /**
     * Creates a new type wiring builder
     *
     * @param typeName the name of the type to wire
     *
     * @return the builder
     */
    public static Builder newTypeWiring(String typeName) {
        assertNotNull(typeName, () -> "You must provide a type name");
        return new Builder().typeName(typeName);
    }

    /**
     * This form allows a lambda to be used as the builder
     *
     * @param typeName        the name of the type to wire
     * @param builderFunction a function that will be given the builder to use
     *
     * @return the same builder back please
     */
    public static TypeRuntimeWiring newTypeWiring(String typeName, UnaryOperator<Builder> builderFunction) {
        return builderFunction.apply(newTypeWiring(typeName)).build();
    }

    public String getTypeName() {
        return typeName;
    }

    public Map<String, DataFetcher> getFieldDataFetchers() {
        return fieldDataFetchers;
    }

    public DataFetcher getDefaultDataFetcher() {
        return defaultDataFetcher;
    }

    public TypeResolver getTypeResolver() {
        return typeResolver;
    }

    public EnumValuesProvider getEnumValuesProvider() {
        return enumValuesProvider;
    }

    public static class Builder {
        private final Map<String, DataFetcher> fieldDataFetchers = new LinkedHashMap<>();
        private String typeName;
        private DataFetcher defaultDataFetcher;
        private TypeResolver typeResolver;
        private EnumValuesProvider enumValuesProvider;
        private boolean strictMode = DEFAULT_STRICT_MODE.get();

        /**
         * Sets the type name for this type wiring.  You MUST set this.
         *
         * @param typeName the name of the type
         *
         * @return the current type wiring
         */
        public Builder typeName(String typeName) {
            this.typeName = typeName;
            return this;
        }

        /**
         * This puts the builder into strict mode, so if things get defined twice, for example, it
         * will throw a {@link StrictModeWiringException}.
         *
         * @return this builder
         */
        public Builder strictMode(boolean strictMode) {
            this.strictMode = strictMode;
            return this;
        }

        /**
         * This puts the builder into strict mode, so if things get defined twice, for example, it
         * will throw a {@link StrictModeWiringException}.
         *
         * @return this builder
         *
         * @deprecated use {@link #strictMode(boolean)} instead
         */
        @Deprecated(since = "2025-03-22", forRemoval = true)
        public Builder strictMode() {
            this.strictMode = true;
            return this;
        }

        /**
         * Adds a data fetcher for the current type to the specified field
         *
         * @param fieldName   the field that data fetcher should apply to
         * @param dataFetcher the new data Fetcher
         *
         * @return the current type wiring
         */
        public Builder dataFetcher(String fieldName, DataFetcher dataFetcher) {
            assertNotNull(dataFetcher, () -> "you must provide a data fetcher");
            assertNotNull(fieldName, () -> "you must tell us what field");
            if (strictMode) {
                assertFieldStrictly(fieldName);
            }
            fieldDataFetchers.put(fieldName, dataFetcher);
            return this;
        }

        /**
         * Adds data fetchers for the current type to the specified field
         *
         * @param dataFetchersMap a map of fields to data fetchers
         *
         * @return the current type wiring
         */
        public Builder dataFetchers(Map<String, DataFetcher> dataFetchersMap) {
            assertNotNull(dataFetchersMap, () -> "you must provide a data fetchers map");
            if (strictMode) {
                dataFetchersMap.forEach((fieldName, df) -> {
                    assertFieldStrictly(fieldName);
                });
            }
            fieldDataFetchers.putAll(dataFetchersMap);
            return this;
        }

        private void assertFieldStrictly(String fieldName) {
            if (fieldDataFetchers.containsKey(fieldName)) {
                throw new StrictModeWiringException(format("The field %s already has a data fetcher defined", fieldName));
            }
        }

        /**
         * All fields in a type need a data fetcher of some sort and this method is called to provide the default data fetcher
         * that will be used for this type if no specific one has been provided per field.
         *
         * @param dataFetcher the default data fetcher to use for this type
         *
         * @return the current type wiring
         */
        public Builder defaultDataFetcher(DataFetcher dataFetcher) {
            assertNotNull(dataFetcher);
            if (strictMode && defaultDataFetcher != null) {
                throw new StrictModeWiringException(format("The type %s has already has a default data fetcher defined", typeName));
            }
            defaultDataFetcher = dataFetcher;
            return this;
        }

        /**
         * Adds a {@link TypeResolver} to the current type.  This MUST be specified for Interface
         * and Union types.
         *
         * @param typeResolver the type resolver in play
         *
         * @return the current type wiring
         */
        public Builder typeResolver(TypeResolver typeResolver) {
            assertNotNull(typeResolver, () -> "you must provide a type resolver");
            this.typeResolver = typeResolver;
            return this;
        }

        public Builder enumValues(EnumValuesProvider enumValuesProvider) {
            assertNotNull(enumValuesProvider, () -> "you must provide an enum values provider");
            this.enumValuesProvider = enumValuesProvider;
            return this;
        }

        /**
         * @return the built type wiring
         */
        public TypeRuntimeWiring build() {
            assertNotNull(typeName, () -> "you must provide a type name");
            return new TypeRuntimeWiring(typeName, defaultDataFetcher, fieldDataFetchers, typeResolver, enumValuesProvider);
        }
    }

}
