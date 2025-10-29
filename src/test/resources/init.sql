INSERT INTO kestra (KEY, VALUE)
    VALUES ("a-doc",
            { "c_string" : "Kestra Doc",
                "c_null": NULL,
                "c_boolean": TRUE,
                "c_int": 3,
                "c_decimal": 3.10,
                "c_decimal_e_notation": 3E3,
                "c_number_array": [3, 3.10, 3E3],
                "c_string_array": ["firstString", "secondString"],
                "c_object":{
                    "c_object_prop": "hello",
                    "c_subobject": {
                        "c_subobject_prop": 5
                        }
                    },
                "c_date": "2006-01-02T15:04:05.567+08:00"
                });

CREATE
    PRIMARY INDEX ON kestra;

CREATE
    PRIMARY INDEX ON kestra.`some-scope`.`some-collection`;

BUILD INDEX ON kestra(`#primary`);

BUILD INDEX ON kestra.`some-scope`.`some-collection`(`#primary`);

INSERT INTO kestra.`some-scope`.`some-collection` (KEY, VALUE)
    VALUES ("a-scoped-collection-doc",
            { "c_string" : "A collection doc" });