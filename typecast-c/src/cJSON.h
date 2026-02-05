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

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
  THE SOFTWARE.
*/

#ifndef cJSON__h
#define cJSON__h

#ifdef __cplusplus
extern "C"
{
#endif

#include <stddef.h>

/* cJSON Types: */
#define cJSON_Invalid (0)
#define cJSON_False  (1 << 0)
#define cJSON_True   (1 << 1)
#define cJSON_NULL   (1 << 2)
#define cJSON_Number (1 << 3)
#define cJSON_String (1 << 4)
#define cJSON_Array  (1 << 5)
#define cJSON_Object (1 << 6)
#define cJSON_Raw    (1 << 7) /* raw json */

#define cJSON_IsReference 256
#define cJSON_StringIsConst 512

/* The cJSON structure: */
typedef struct cJSON
{
    struct cJSON *next;
    struct cJSON *prev;
    struct cJSON *child;
    int type;
    char *valuestring;
    int valueint;
    double valuedouble;
    char *string;
} cJSON;

typedef struct cJSON_Hooks
{
      void *(*malloc_fn)(size_t sz);
      void (*free_fn)(void *ptr);
} cJSON_Hooks;

/* Limits how deeply nested arrays/objects can be before cJSON rejects to parse them. */
#ifndef CJSON_NESTING_LIMIT
#define CJSON_NESTING_LIMIT 1000
#endif

/* Supply malloc, realloc and free functions to cJSON */
extern void cJSON_InitHooks(cJSON_Hooks* hooks);

/* Memory Management: the caller is always responsible to free the results from all variants of cJSON_Parse and cJSON_Print. */
extern cJSON *cJSON_Parse(const char *value);
extern cJSON *cJSON_ParseWithLength(const char *value, size_t buffer_length);
extern char *cJSON_Print(const cJSON *item);
extern char *cJSON_PrintUnformatted(const cJSON *item);
extern void cJSON_Delete(cJSON *item);

/* Returns the number of items in an array (or object). */
extern int cJSON_GetArraySize(const cJSON *array);
extern cJSON *cJSON_GetArrayItem(const cJSON *array, int index);
extern cJSON *cJSON_GetObjectItem(const cJSON *const object, const char *const string);
extern cJSON *cJSON_GetObjectItemCaseSensitive(const cJSON *const object, const char *const string);
extern int cJSON_HasObjectItem(const cJSON *object, const char *string);

/* Check item type and return its value */
extern char *cJSON_GetStringValue(const cJSON *const item);
extern double cJSON_GetNumberValue(const cJSON *const item);

/* These functions check the type of an item */
extern int cJSON_IsInvalid(const cJSON *const item);
extern int cJSON_IsFalse(const cJSON *const item);
extern int cJSON_IsTrue(const cJSON *const item);
extern int cJSON_IsBool(const cJSON *const item);
extern int cJSON_IsNull(const cJSON *const item);
extern int cJSON_IsNumber(const cJSON *const item);
extern int cJSON_IsString(const cJSON *const item);
extern int cJSON_IsArray(const cJSON *const item);
extern int cJSON_IsObject(const cJSON *const item);
extern int cJSON_IsRaw(const cJSON *const item);

/* Create items */
extern cJSON *cJSON_CreateNull(void);
extern cJSON *cJSON_CreateTrue(void);
extern cJSON *cJSON_CreateFalse(void);
extern cJSON *cJSON_CreateBool(int boolean);
extern cJSON *cJSON_CreateNumber(double num);
extern cJSON *cJSON_CreateString(const char *string);
extern cJSON *cJSON_CreateRaw(const char *raw);
extern cJSON *cJSON_CreateArray(void);
extern cJSON *cJSON_CreateObject(void);

/* Append item to the specified array/object. */
extern int cJSON_AddItemToArray(cJSON *array, cJSON *item);
extern int cJSON_AddItemToObject(cJSON *object, const char *string, cJSON *item);

/* Helper functions for creating and adding items to an object at the same time. */
extern cJSON *cJSON_AddNullToObject(cJSON *const object, const char *const name);
extern cJSON *cJSON_AddTrueToObject(cJSON *const object, const char *const name);
extern cJSON *cJSON_AddFalseToObject(cJSON *const object, const char *const name);
extern cJSON *cJSON_AddBoolToObject(cJSON *const object, const char *const name, const int boolean);
extern cJSON *cJSON_AddNumberToObject(cJSON *const object, const char *const name, const double number);
extern cJSON *cJSON_AddStringToObject(cJSON *const object, const char *const name, const char *const string);
extern cJSON *cJSON_AddRawToObject(cJSON *const object, const char *const name, const char *const raw);
extern cJSON *cJSON_AddObjectToObject(cJSON *const object, const char *const name);
extern cJSON *cJSON_AddArrayToObject(cJSON *const object, const char *const name);

/* Duplicate a cJSON item */
extern cJSON *cJSON_Duplicate(const cJSON *item, int recurse);

/* Minify a strings, remove blank characters(such as ' ', '\t', '\r', '\n') from strings. */
extern void cJSON_Minify(char *json);

/* Macro for iterating over an array or object */
#define cJSON_ArrayForEach(element, array) \
    for(element = (array != NULL) ? (array)->child : NULL; element != NULL; element = element->next)

/* malloc/free objects using the malloc/free functions that have been set with cJSON_InitHooks */
extern void *cJSON_malloc(size_t size);
extern void cJSON_free(void *object);

#ifdef __cplusplus
}
#endif

#endif
