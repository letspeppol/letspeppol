# OpenAPI Syncables Spec

Add `syncables` in the root of an OpenAPI Document (OAD), next to `info`, `paths` and `components`.
In there each entry defines either an object or a collection that is syncable, and declarative instructions for a sync engine on how to do this.

For now, the only things you can specify are:
* `description` (human-readable information)
* `type` (should be 'collection' or 'object')
* `schema` (of the object, or of each of the items in the collection)
* `get` (how to fetch the whole collection or object)
  * `path` (which entry from the OAD's `paths` to use)
  * `operation` (within that path, which operation to use)
  * `field` (optional, within the response body, which field contains the collection or the object that was fetched - schema should match)
* `add` (how to add an item to a collection, can not be used for objects)
  * `path` (which entry from the OAD's `paths` to use)
  * `operation` (within that path, which operation to use)
  * `field` (optional, within the request body, which field should contain the item to add - schema should match)
