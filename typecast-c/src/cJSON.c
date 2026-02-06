/*
  Copyright (c) 2009-2017 Dave Gamble and cJSON contributors

  Permission is hereby granted, free of charge, to any person obtaining a copy
  of this software and associated documentation files (the "Software"), to deal
  in the Software without restriction, including without limitation the rights
  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  copies of the Software, and to permit persons to whom the Software is
  furnished to do so, subject to the following conditions:

  The above copyright notice and this permission notice shall be included in
  all copies or substantial portions of the Software.
*/

/* cJSON - Minimal JSON parser implementation */

#include <string.h>
#include <stdio.h>
#include <math.h>
#include <stdlib.h>
#include <limits.h>
#include <ctype.h>
#include <float.h>

#if defined(_WIN32) || defined(_WIN64)
    #define strcasecmp _stricmp
#endif

#include "cJSON.h"

/* Memory hooks */
static void *(*global_malloc)(size_t sz) = malloc;
static void (*global_free)(void *ptr) = free;

void cJSON_InitHooks(cJSON_Hooks* hooks)
{
    if (hooks == NULL) {
        global_malloc = malloc;
        global_free = free;
        return;
    }
    global_malloc = hooks->malloc_fn ? hooks->malloc_fn : malloc;
    global_free = hooks->free_fn ? hooks->free_fn : free;
}

void *cJSON_malloc(size_t size)
{
    return global_malloc(size);
}

void cJSON_free(void *object)
{
    global_free(object);
}

static cJSON *cJSON_New_Item(void)
{
    cJSON* node = (cJSON*)global_malloc(sizeof(cJSON));
    if (node) {
        memset(node, 0, sizeof(cJSON));
    }
    return node;
}

void cJSON_Delete(cJSON *item)
{
    cJSON *next = NULL;
    while (item != NULL)
    {
        next = item->next;
        if (!(item->type & cJSON_IsReference) && (item->child != NULL))
        {
            cJSON_Delete(item->child);
        }
        if (!(item->type & cJSON_IsReference) && (item->valuestring != NULL))
        {
            global_free(item->valuestring);
        }
        if (!(item->type & cJSON_StringIsConst) && (item->string != NULL))
        {
            global_free(item->string);
        }
        global_free(item);
        item = next;
    }
}

/* Parser utilities */
typedef struct
{
    const unsigned char *content;
    size_t length;
    size_t offset;
    size_t depth;
} parse_buffer;

#define can_read(buffer, size) ((buffer != NULL) && (((buffer)->offset + size) <= (buffer)->length))
#define can_access_at_index(buffer, index) ((buffer != NULL) && (((buffer)->offset + index) < (buffer)->length))
#define cannot_access_at_index(buffer, index) (!can_access_at_index(buffer, index))
#define buffer_at_offset(buffer) ((buffer)->content + (buffer)->offset)

static parse_buffer *buffer_skip_whitespace(parse_buffer *buffer)
{
    if ((buffer == NULL) || (buffer->content == NULL)) return NULL;
    
    while (can_access_at_index(buffer, 0) && (buffer_at_offset(buffer)[0] <= 32))
    {
        buffer->offset++;
    }
    
    if (buffer->offset == buffer->length)
    {
        buffer->offset--;
    }
    
    return buffer;
}

static unsigned char get_decimal_point(void)
{
    return '.';
}

/* Parse number */
static int parse_number(cJSON *item, parse_buffer *buffer)
{
    double number = 0;
    unsigned char *after_end = NULL;
    unsigned char number_c_string[64];
    size_t i = 0;
    
    if ((buffer == NULL) || (buffer->content == NULL)) return 0;
    
    for (i = 0; (i < (sizeof(number_c_string) - 1)) && can_access_at_index(buffer, i); i++)
    {
        switch (buffer_at_offset(buffer)[i])
        {
            case '0': case '1': case '2': case '3': case '4':
            case '5': case '6': case '7': case '8': case '9':
            case '+': case '-': case 'e': case 'E':
                number_c_string[i] = buffer_at_offset(buffer)[i];
                break;
            case '.':
                number_c_string[i] = get_decimal_point();
                break;
            default:
                goto loop_end;
        }
    }
loop_end:
    number_c_string[i] = '\0';
    
    number = strtod((const char*)number_c_string, (char**)&after_end);
    if (number_c_string == after_end) return 0;
    
    item->valuedouble = number;
    item->valueint = (int)number;
    item->type = cJSON_Number;
    buffer->offset += (size_t)(after_end - number_c_string);
    
    return 1;
}

/* Parse string */
static int parse_string(cJSON *item, parse_buffer *buffer)
{
    const unsigned char *input_pointer = buffer_at_offset(buffer) + 1;
    const unsigned char *input_end = buffer_at_offset(buffer) + 1;
    unsigned char *output_pointer = NULL;
    unsigned char *output = NULL;
    
    if (buffer_at_offset(buffer)[0] != '\"') return 0;
    
    /* Calculate length */
    {
        size_t allocation_length = 0;
        size_t skipped_bytes = 0;
        while ((input_end < buffer->content + buffer->length) && (*input_end != '\"'))
        {
            if (input_end[0] == '\\')
            {
                if ((size_t)(input_end + 1 - buffer->content) >= buffer->length) return 0;
                skipped_bytes++;
                input_end++;
            }
            input_end++;
        }
        if ((input_end < buffer->content) || (*input_end != '\"')) return 0;
        
        allocation_length = (size_t)(input_end - buffer_at_offset(buffer)) - skipped_bytes;
        output = (unsigned char*)global_malloc(allocation_length + 1);
        if (output == NULL) return 0;
    }
    
    output_pointer = output;
    while (input_pointer < input_end)
    {
        if (*input_pointer != '\\')
        {
            *output_pointer++ = *input_pointer++;
        }
        else
        {
            unsigned char sequence_length = 2;
            if ((input_end - input_pointer) < 1) break;
            
            switch (input_pointer[1])
            {
                case 'b': *output_pointer++ = '\b'; break;
                case 'f': *output_pointer++ = '\f'; break;
                case 'n': *output_pointer++ = '\n'; break;
                case 'r': *output_pointer++ = '\r'; break;
                case 't': *output_pointer++ = '\t'; break;
                case '\"': case '\\': case '/':
                    *output_pointer++ = input_pointer[1];
                    break;
                case 'u':
                    /* Skip unicode escapes for simplicity */
                    sequence_length = 6;
                    *output_pointer++ = '?';
                    break;
                default:
                    global_free(output);
                    return 0;
            }
            input_pointer += sequence_length;
        }
    }
    
    *output_pointer = '\0';
    item->type = cJSON_String;
    item->valuestring = (char*)output;
    buffer->offset = (size_t)(input_end - buffer->content) + 1;
    
    return 1;
}

/* Forward declaration */
static int parse_value(cJSON *item, parse_buffer *buffer);

/* Parse array */
static int parse_array(cJSON *item, parse_buffer *buffer)
{
    cJSON *head = NULL;
    cJSON *current_item = NULL;
    
    if (buffer->depth >= CJSON_NESTING_LIMIT) return 0;
    buffer->depth++;
    
    if (buffer_at_offset(buffer)[0] != '[') goto fail;
    
    buffer->offset++;
    buffer_skip_whitespace(buffer);
    if (can_access_at_index(buffer, 0) && (buffer_at_offset(buffer)[0] == ']'))
    {
        goto success;
    }
    
    if (cannot_access_at_index(buffer, 0))
    {
        buffer->offset--;
        goto fail;
    }
    
    buffer->offset--;
    do
    {
        cJSON *new_item = cJSON_New_Item();
        if (new_item == NULL) goto fail;
        
        if (head == NULL)
        {
            current_item = head = new_item;
        }
        else
        {
            current_item->next = new_item;
            new_item->prev = current_item;
            current_item = new_item;
        }
        
        buffer->offset++;
        buffer_skip_whitespace(buffer);
        if (!parse_value(current_item, buffer)) goto fail;
        buffer_skip_whitespace(buffer);
    }
    while (can_access_at_index(buffer, 0) && (buffer_at_offset(buffer)[0] == ','));
    
    if (cannot_access_at_index(buffer, 0) || buffer_at_offset(buffer)[0] != ']')
    {
        goto fail;
    }
    
success:
    buffer->depth--;
    item->type = cJSON_Array;
    item->child = head;
    buffer->offset++;
    return 1;

fail:
    if (head != NULL) cJSON_Delete(head);
    return 0;
}

/* Parse object */
static int parse_object(cJSON *item, parse_buffer *buffer)
{
    cJSON *head = NULL;
    cJSON *current_item = NULL;
    
    if (buffer->depth >= CJSON_NESTING_LIMIT) return 0;
    buffer->depth++;
    
    if (cannot_access_at_index(buffer, 0) || (buffer_at_offset(buffer)[0] != '{'))
    {
        goto fail;
    }
    
    buffer->offset++;
    buffer_skip_whitespace(buffer);
    if (can_access_at_index(buffer, 0) && (buffer_at_offset(buffer)[0] == '}'))
    {
        goto success;
    }
    
    if (cannot_access_at_index(buffer, 0))
    {
        buffer->offset--;
        goto fail;
    }
    
    buffer->offset--;
    do
    {
        cJSON *new_item = cJSON_New_Item();
        if (new_item == NULL) goto fail;
        
        if (head == NULL)
        {
            current_item = head = new_item;
        }
        else
        {
            current_item->next = new_item;
            new_item->prev = current_item;
            current_item = new_item;
        }
        
        buffer->offset++;
        buffer_skip_whitespace(buffer);
        if (!parse_string(current_item, buffer)) goto fail;
        buffer_skip_whitespace(buffer);
        
        current_item->string = current_item->valuestring;
        current_item->valuestring = NULL;
        
        if (cannot_access_at_index(buffer, 0) || (buffer_at_offset(buffer)[0] != ':'))
        {
            goto fail;
        }
        
        buffer->offset++;
        buffer_skip_whitespace(buffer);
        if (!parse_value(current_item, buffer)) goto fail;
        buffer_skip_whitespace(buffer);
    }
    while (can_access_at_index(buffer, 0) && (buffer_at_offset(buffer)[0] == ','));
    
    if (cannot_access_at_index(buffer, 0) || (buffer_at_offset(buffer)[0] != '}'))
    {
        goto fail;
    }
    
success:
    buffer->depth--;
    item->type = cJSON_Object;
    item->child = head;
    buffer->offset++;
    return 1;

fail:
    if (head != NULL) cJSON_Delete(head);
    return 0;
}

/* Parse value */
static int parse_value(cJSON *item, parse_buffer *buffer)
{
    if ((buffer == NULL) || (buffer->content == NULL)) return 0;
    
    buffer_skip_whitespace(buffer);
    if (cannot_access_at_index(buffer, 0)) return 0;
    
    /* null */
    if (can_read(buffer, 4) && (strncmp((const char*)buffer_at_offset(buffer), "null", 4) == 0))
    {
        item->type = cJSON_NULL;
        buffer->offset += 4;
        return 1;
    }
    
    /* false */
    if (can_read(buffer, 5) && (strncmp((const char*)buffer_at_offset(buffer), "false", 5) == 0))
    {
        item->type = cJSON_False;
        buffer->offset += 5;
        return 1;
    }
    
    /* true */
    if (can_read(buffer, 4) && (strncmp((const char*)buffer_at_offset(buffer), "true", 4) == 0))
    {
        item->type = cJSON_True;
        buffer->offset += 4;
        return 1;
    }
    
    /* string */
    if (can_access_at_index(buffer, 0) && (buffer_at_offset(buffer)[0] == '\"'))
    {
        return parse_string(item, buffer);
    }
    
    /* number */
    if (can_access_at_index(buffer, 0) && ((buffer_at_offset(buffer)[0] == '-') ||
        ((buffer_at_offset(buffer)[0] >= '0') && (buffer_at_offset(buffer)[0] <= '9'))))
    {
        return parse_number(item, buffer);
    }
    
    /* array */
    if (can_access_at_index(buffer, 0) && (buffer_at_offset(buffer)[0] == '['))
    {
        return parse_array(item, buffer);
    }
    
    /* object */
    if (can_access_at_index(buffer, 0) && (buffer_at_offset(buffer)[0] == '{'))
    {
        return parse_object(item, buffer);
    }
    
    return 0;
}

cJSON *cJSON_ParseWithLength(const char *value, size_t buffer_length)
{
    parse_buffer buffer = { 0 };
    cJSON *item = NULL;
    
    if (value == NULL || buffer_length == 0) return NULL;
    
    item = cJSON_New_Item();
    if (item == NULL) return NULL;
    
    buffer.content = (const unsigned char*)value;
    buffer.length = buffer_length;
    buffer.offset = 0;
    buffer.depth = 0;
    
    if (!parse_value(item, &buffer))
    {
        cJSON_Delete(item);
        return NULL;
    }
    
    return item;
}

cJSON *cJSON_Parse(const char *value)
{
    return cJSON_ParseWithLength(value, value ? strlen(value) : 0);
}

/* Print utilities */
typedef struct
{
    unsigned char *buffer;
    size_t length;
    size_t offset;
    size_t depth;
    int format;
} printbuffer;

static unsigned char* ensure(printbuffer *p, size_t needed)
{
    unsigned char *newbuffer = NULL;
    size_t newsize = 0;
    
    if ((p == NULL) || (p->buffer == NULL)) return NULL;
    
    if ((p->offset + needed) <= p->length) return p->buffer + p->offset;
    
    newsize = (p->offset + needed) * 2;
    newbuffer = (unsigned char*)global_malloc(newsize);
    if (newbuffer == NULL) return NULL;
    
    memcpy(newbuffer, p->buffer, p->offset + 1);
    global_free(p->buffer);
    p->buffer = newbuffer;
    p->length = newsize;
    
    return newbuffer + p->offset;
}

static void update_offset(printbuffer *buffer)
{
    buffer->offset += strlen((const char*)buffer->buffer + buffer->offset);
}

/* Forward declarations */
static int print_value(const cJSON *item, printbuffer *buffer);
static int print_string_ptr(const unsigned char *input, printbuffer *buffer);

static int print_number(const cJSON *item, printbuffer *buffer)
{
    unsigned char *output = NULL;
    double d = item->valuedouble;
    int length = 0;
    
    output = ensure(buffer, 64);
    if (output == NULL) return 0;
    
    if (d == d && (d != d - d) && ((d * 0) == 0))
    {
        length = sprintf((char*)output, "%.15g", d);
    }
    else
    {
        length = sprintf((char*)output, "null");
    }
    
    buffer->offset += (size_t)length;
    return 1;
}

static int print_string(const cJSON *item, printbuffer *buffer)
{
    return print_string_ptr((unsigned char*)item->valuestring, buffer);
}

static int print_string_ptr(const unsigned char *input, printbuffer *buffer)
{
    const unsigned char *input_pointer = NULL;
    unsigned char *output = NULL;
    unsigned char *output_pointer = NULL;
    size_t output_length = 0;
    size_t escape_characters = 0;
    
    if (input == NULL) input = (unsigned char*)"";
    
    for (input_pointer = input; *input_pointer; input_pointer++)
    {
        switch (*input_pointer)
        {
            case '\"': case '\\': case '\b': case '\f':
            case '\n': case '\r': case '\t':
                escape_characters++;
                break;
            default:
                if (*input_pointer < 32) escape_characters += 5;
                break;
        }
    }
    output_length = (size_t)(input_pointer - input) + escape_characters + 2;
    
    output = ensure(buffer, output_length + 1);
    if (output == NULL) return 0;
    
    output_pointer = output;
    *output_pointer++ = '\"';
    
    for (input_pointer = input; *input_pointer; input_pointer++)
    {
        if ((*input_pointer > 31) && (*input_pointer != '\"') && (*input_pointer != '\\'))
        {
            *output_pointer++ = *input_pointer;
        }
        else
        {
            *output_pointer++ = '\\';
            switch (*input_pointer)
            {
                case '\\': *output_pointer++ = '\\'; break;
                case '\"': *output_pointer++ = '\"'; break;
                case '\b': *output_pointer++ = 'b'; break;
                case '\f': *output_pointer++ = 'f'; break;
                case '\n': *output_pointer++ = 'n'; break;
                case '\r': *output_pointer++ = 'r'; break;
                case '\t': *output_pointer++ = 't'; break;
                default:
                    sprintf((char*)output_pointer, "u%04x", *input_pointer);
                    output_pointer += 5;
                    break;
            }
        }
    }
    
    *output_pointer++ = '\"';
    *output_pointer = '\0';
    buffer->offset += output_length;
    
    return 1;
}

static int print_array(const cJSON *item, printbuffer *buffer)
{
    unsigned char *output_pointer = NULL;
    cJSON *current_element = item->child;
    
    output_pointer = ensure(buffer, 1);
    if (output_pointer == NULL) return 0;
    *output_pointer = '[';
    buffer->offset++;
    buffer->depth++;
    
    while (current_element != NULL)
    {
        if (!print_value(current_element, buffer)) return 0;
        
        if (current_element->next)
        {
            output_pointer = ensure(buffer, 1);
            if (output_pointer == NULL) return 0;
            *output_pointer = ',';
            buffer->offset++;
        }
        current_element = current_element->next;
    }
    
    output_pointer = ensure(buffer, 1);
    if (output_pointer == NULL) return 0;
    *output_pointer = ']';
    buffer->offset++;
    buffer->depth--;
    
    return 1;
}

static int print_object(const cJSON *item, printbuffer *buffer)
{
    unsigned char *output_pointer = NULL;
    cJSON *current_item = item->child;
    
    output_pointer = ensure(buffer, 1);
    if (output_pointer == NULL) return 0;
    *output_pointer = '{';
    buffer->offset++;
    buffer->depth++;
    
    while (current_item)
    {
        if (!print_string_ptr((unsigned char*)current_item->string, buffer)) return 0;
        
        output_pointer = ensure(buffer, 1);
        if (output_pointer == NULL) return 0;
        *output_pointer = ':';
        buffer->offset++;
        
        if (!print_value(current_item, buffer)) return 0;
        
        if (current_item->next)
        {
            output_pointer = ensure(buffer, 1);
            if (output_pointer == NULL) return 0;
            *output_pointer = ',';
            buffer->offset++;
        }
        current_item = current_item->next;
    }
    
    output_pointer = ensure(buffer, 1);
    if (output_pointer == NULL) return 0;
    *output_pointer = '}';
    buffer->offset++;
    buffer->depth--;
    
    return 1;
}

static int print_value(const cJSON *item, printbuffer *buffer)
{
    unsigned char *output = NULL;
    
    if ((item == NULL) || (buffer == NULL)) return 0;
    
    switch ((item->type) & 0xFF)
    {
        case cJSON_NULL:
            output = ensure(buffer, 5);
            if (output == NULL) return 0;
            strcpy((char*)output, "null");
            buffer->offset += 4;
            return 1;
            
        case cJSON_False:
            output = ensure(buffer, 6);
            if (output == NULL) return 0;
            strcpy((char*)output, "false");
            buffer->offset += 5;
            return 1;
            
        case cJSON_True:
            output = ensure(buffer, 5);
            if (output == NULL) return 0;
            strcpy((char*)output, "true");
            buffer->offset += 4;
            return 1;
            
        case cJSON_Number:
            return print_number(item, buffer);
            
        case cJSON_Raw:
        {
            size_t raw_length = item->valuestring ? strlen(item->valuestring) : 0;
            output = ensure(buffer, raw_length + 1);
            if (output == NULL) return 0;
            memcpy(output, item->valuestring, raw_length);
            buffer->offset += raw_length;
            return 1;
        }
            
        case cJSON_String:
            return print_string(item, buffer);
            
        case cJSON_Array:
            return print_array(item, buffer);
            
        case cJSON_Object:
            return print_object(item, buffer);
            
        default:
            return 0;
    }
}

static char *print(const cJSON *item, int format)
{
    printbuffer buffer = { 0 };
    buffer.buffer = (unsigned char*)global_malloc(256);
    if (buffer.buffer == NULL) return NULL;
    
    buffer.length = 256;
    buffer.offset = 0;
    buffer.depth = 0;
    buffer.format = format;
    
    if (!print_value(item, &buffer))
    {
        global_free(buffer.buffer);
        return NULL;
    }
    
    return (char*)buffer.buffer;
}

char *cJSON_Print(const cJSON *item)
{
    return print(item, 1);
}

char *cJSON_PrintUnformatted(const cJSON *item)
{
    return print(item, 0);
}

/* Array/Object access */
int cJSON_GetArraySize(const cJSON *array)
{
    cJSON *child = NULL;
    size_t size = 0;
    
    if (array == NULL) return 0;
    
    child = array->child;
    while (child != NULL)
    {
        size++;
        child = child->next;
    }
    
    return (int)size;
}

cJSON *cJSON_GetArrayItem(const cJSON *array, int index)
{
    cJSON *current_child = NULL;
    
    if (array == NULL || index < 0) return NULL;
    
    current_child = array->child;
    while ((current_child != NULL) && (index > 0))
    {
        index--;
        current_child = current_child->next;
    }
    
    return current_child;
}

static cJSON *get_object_item(const cJSON *object, const char *name, int case_sensitive)
{
    cJSON *current_element = NULL;
    
    if ((object == NULL) || (name == NULL)) return NULL;
    
    current_element = object->child;
    if (case_sensitive)
    {
        while ((current_element != NULL) && (current_element->string != NULL) &&
               (strcmp(name, current_element->string) != 0))
        {
            current_element = current_element->next;
        }
    }
    else
    {
        while ((current_element != NULL) && (current_element->string != NULL))
        {
            if (strcasecmp(name, current_element->string) == 0)
            {
                break;
            }
            current_element = current_element->next;
        }
    }
    
    return current_element;
}

cJSON *cJSON_GetObjectItem(const cJSON *object, const char *string)
{
    return get_object_item(object, string, 0);
}

cJSON *cJSON_GetObjectItemCaseSensitive(const cJSON *object, const char *string)
{
    return get_object_item(object, string, 1);
}

int cJSON_HasObjectItem(const cJSON *object, const char *string)
{
    return cJSON_GetObjectItem(object, string) ? 1 : 0;
}

/* Type checks */
int cJSON_IsInvalid(const cJSON *item) { return (item == NULL) || ((item->type & 0xFF) == cJSON_Invalid); }
int cJSON_IsFalse(const cJSON *item) { return (item != NULL) && ((item->type & 0xFF) == cJSON_False); }
int cJSON_IsTrue(const cJSON *item) { return (item != NULL) && ((item->type & 0xFF) == cJSON_True); }
int cJSON_IsBool(const cJSON *item) { return (item != NULL) && (((item->type & 0xFF) == cJSON_True) || ((item->type & 0xFF) == cJSON_False)); }
int cJSON_IsNull(const cJSON *item) { return (item != NULL) && ((item->type & 0xFF) == cJSON_NULL); }
int cJSON_IsNumber(const cJSON *item) { return (item != NULL) && ((item->type & 0xFF) == cJSON_Number); }
int cJSON_IsString(const cJSON *item) { return (item != NULL) && ((item->type & 0xFF) == cJSON_String); }
int cJSON_IsArray(const cJSON *item) { return (item != NULL) && ((item->type & 0xFF) == cJSON_Array); }
int cJSON_IsObject(const cJSON *item) { return (item != NULL) && ((item->type & 0xFF) == cJSON_Object); }
int cJSON_IsRaw(const cJSON *item) { return (item != NULL) && ((item->type & 0xFF) == cJSON_Raw); }

char *cJSON_GetStringValue(const cJSON *item)
{
    if (!cJSON_IsString(item)) return NULL;
    return item->valuestring;
}

double cJSON_GetNumberValue(const cJSON *item)
{
    if (!cJSON_IsNumber(item)) return 0.0;
    return item->valuedouble;
}

/* Create functions */
cJSON *cJSON_CreateNull(void)
{
    cJSON *item = cJSON_New_Item();
    if (item) item->type = cJSON_NULL;
    return item;
}

cJSON *cJSON_CreateTrue(void)
{
    cJSON *item = cJSON_New_Item();
    if (item) item->type = cJSON_True;
    return item;
}

cJSON *cJSON_CreateFalse(void)
{
    cJSON *item = cJSON_New_Item();
    if (item) item->type = cJSON_False;
    return item;
}

cJSON *cJSON_CreateBool(int boolean)
{
    cJSON *item = cJSON_New_Item();
    if (item) item->type = boolean ? cJSON_True : cJSON_False;
    return item;
}

cJSON *cJSON_CreateNumber(double num)
{
    cJSON *item = cJSON_New_Item();
    if (item)
    {
        item->type = cJSON_Number;
        item->valuedouble = num;
        item->valueint = (int)num;
    }
    return item;
}

cJSON *cJSON_CreateString(const char *string)
{
    cJSON *item = cJSON_New_Item();
    if (item)
    {
        item->type = cJSON_String;
        item->valuestring = (char*)global_malloc(strlen(string) + 1);
        if (item->valuestring)
        {
            strcpy(item->valuestring, string);
        }
        else
        {
            cJSON_Delete(item);
            return NULL;
        }
    }
    return item;
}

cJSON *cJSON_CreateRaw(const char *raw)
{
    cJSON *item = cJSON_New_Item();
    if (item)
    {
        item->type = cJSON_Raw;
        item->valuestring = (char*)global_malloc(strlen(raw) + 1);
        if (item->valuestring)
        {
            strcpy(item->valuestring, raw);
        }
        else
        {
            cJSON_Delete(item);
            return NULL;
        }
    }
    return item;
}

cJSON *cJSON_CreateArray(void)
{
    cJSON *item = cJSON_New_Item();
    if (item) item->type = cJSON_Array;
    return item;
}

cJSON *cJSON_CreateObject(void)
{
    cJSON *item = cJSON_New_Item();
    if (item) item->type = cJSON_Object;
    return item;
}

/* Add item to array/object */
static int add_item_to_array(cJSON *array, cJSON *item)
{
    cJSON *child = NULL;
    
    if ((item == NULL) || (array == NULL) || (array == item)) return 0;
    
    child = array->child;
    if (child == NULL)
    {
        array->child = item;
        item->prev = item;
        item->next = NULL;
    }
    else
    {
        while (child->next) child = child->next;
        child->next = item;
        item->prev = child;
        item->next = NULL;
    }
    
    return 1;
}

int cJSON_AddItemToArray(cJSON *array, cJSON *item)
{
    return add_item_to_array(array, item);
}

static int add_item_to_object(cJSON *object, const char *string, cJSON *item, int constant_key)
{
    char *new_key = NULL;
    int new_type = cJSON_Invalid;
    
    if ((object == NULL) || (string == NULL) || (item == NULL) || (object == item))
    {
        return 0;
    }
    
    if (constant_key)
    {
        new_key = (char*)string;
        new_type = item->type | cJSON_StringIsConst;
    }
    else
    {
        new_key = (char*)global_malloc(strlen(string) + 1);
        if (new_key == NULL) return 0;
        strcpy(new_key, string);
        new_type = item->type & ~cJSON_StringIsConst;
    }
    
    if (!(item->type & cJSON_StringIsConst) && (item->string != NULL))
    {
        global_free(item->string);
    }
    
    item->string = new_key;
    item->type = new_type;
    
    return add_item_to_array(object, item);
}

int cJSON_AddItemToObject(cJSON *object, const char *string, cJSON *item)
{
    return add_item_to_object(object, string, item, 0);
}

/* Helper functions to add items directly */
cJSON *cJSON_AddNullToObject(cJSON *object, const char *name)
{
    cJSON *null_item = cJSON_CreateNull();
    if (add_item_to_object(object, name, null_item, 0)) return null_item;
    cJSON_Delete(null_item);
    return NULL;
}

cJSON *cJSON_AddTrueToObject(cJSON *object, const char *name)
{
    cJSON *true_item = cJSON_CreateTrue();
    if (add_item_to_object(object, name, true_item, 0)) return true_item;
    cJSON_Delete(true_item);
    return NULL;
}

cJSON *cJSON_AddFalseToObject(cJSON *object, const char *name)
{
    cJSON *false_item = cJSON_CreateFalse();
    if (add_item_to_object(object, name, false_item, 0)) return false_item;
    cJSON_Delete(false_item);
    return NULL;
}

cJSON *cJSON_AddBoolToObject(cJSON *object, const char *name, int boolean)
{
    cJSON *bool_item = cJSON_CreateBool(boolean);
    if (add_item_to_object(object, name, bool_item, 0)) return bool_item;
    cJSON_Delete(bool_item);
    return NULL;
}

cJSON *cJSON_AddNumberToObject(cJSON *object, const char *name, double number)
{
    cJSON *number_item = cJSON_CreateNumber(number);
    if (add_item_to_object(object, name, number_item, 0)) return number_item;
    cJSON_Delete(number_item);
    return NULL;
}

cJSON *cJSON_AddStringToObject(cJSON *object, const char *name, const char *string)
{
    cJSON *string_item = cJSON_CreateString(string);
    if (add_item_to_object(object, name, string_item, 0)) return string_item;
    cJSON_Delete(string_item);
    return NULL;
}

cJSON *cJSON_AddRawToObject(cJSON *object, const char *name, const char *raw)
{
    cJSON *raw_item = cJSON_CreateRaw(raw);
    if (add_item_to_object(object, name, raw_item, 0)) return raw_item;
    cJSON_Delete(raw_item);
    return NULL;
}

cJSON *cJSON_AddObjectToObject(cJSON *object, const char *name)
{
    cJSON *new_obj = cJSON_CreateObject();
    if (add_item_to_object(object, name, new_obj, 0)) return new_obj;
    cJSON_Delete(new_obj);
    return NULL;
}

cJSON *cJSON_AddArrayToObject(cJSON *object, const char *name)
{
    cJSON *new_arr = cJSON_CreateArray();
    if (add_item_to_object(object, name, new_arr, 0)) return new_arr;
    cJSON_Delete(new_arr);
    return NULL;
}

/* Duplicate */
cJSON *cJSON_Duplicate(const cJSON *item, int recurse)
{
    cJSON *newitem = NULL;
    cJSON *child = NULL;
    cJSON *next = NULL;
    cJSON *newchild = NULL;
    
    if (!item) return NULL;
    
    newitem = cJSON_New_Item();
    if (!newitem) return NULL;
    
    newitem->type = item->type & (~cJSON_IsReference);
    newitem->valueint = item->valueint;
    newitem->valuedouble = item->valuedouble;
    
    if (item->valuestring)
    {
        newitem->valuestring = (char*)global_malloc(strlen(item->valuestring) + 1);
        if (!newitem->valuestring)
        {
            cJSON_Delete(newitem);
            return NULL;
        }
        strcpy(newitem->valuestring, item->valuestring);
    }
    
    if (item->string)
    {
        newitem->string = (item->type & cJSON_StringIsConst) ? item->string :
            (char*)global_malloc(strlen(item->string) + 1);
        if (!newitem->string)
        {
            cJSON_Delete(newitem);
            return NULL;
        }
        if (!(item->type & cJSON_StringIsConst))
        {
            strcpy(newitem->string, item->string);
        }
    }
    
    if (!recurse) return newitem;
    
    child = item->child;
    while (child != NULL)
    {
        newchild = cJSON_Duplicate(child, 1);
        if (!newchild)
        {
            cJSON_Delete(newitem);
            return NULL;
        }
        
        if (next != NULL)
        {
            next->next = newchild;
            newchild->prev = next;
            next = newchild;
        }
        else
        {
            newitem->child = newchild;
            next = newchild;
        }
        
        child = child->next;
    }
    
    if (newitem->child)
    {
        newitem->child->prev = next;
    }
    
    return newitem;
}

/* Minify */
void cJSON_Minify(char *json)
{
    unsigned char *into = (unsigned char*)json;
    
    if (json == NULL) return;
    
    while (*json)
    {
        if (*json == ' ' || *json == '\t' || *json == '\r' || *json == '\n')
        {
            json++;
        }
        else if ((*json == '/') && (json[1] == '/'))
        {
            while (*json && (*json != '\n')) json++;
        }
        else if ((*json == '/') && (json[1] == '*'))
        {
            while (*json && !((*json == '*') && (json[1] == '/'))) json++;
            json += 2;
        }
        else if (*json == '\"')
        {
            *into++ = (unsigned char)*json++;
            while (*json && (*json != '\"'))
            {
                if (*json == '\\') *into++ = (unsigned char)*json++;
                *into++ = (unsigned char)*json++;
            }
            if (*json) *into++ = (unsigned char)*json++;
        }
        else
        {
            *into++ = (unsigned char)*json++;
        }
    }
    
    *into = '\0';
}
