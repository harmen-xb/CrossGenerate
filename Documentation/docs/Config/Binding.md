# Binding configuration

## Binding

### Syntax
``` xml
<Binding>
  <SectionModelBinding ... >
    ...
  </SectionModelBinding>
</Binding>
```

### Child sections
| Section                                      | Description |
|:---                                              |:--- |
| SectionModelBinding               | See [SectionModelBinding](#sectionmodelbinding) |


## SectionModelBinding
A SectionModelBinding defines how the model should be mapped to the template.
### Syntax
``` xml
<SectionModelBinding
  section="<section-name>"
  modelXPath="<model-xpath>"
  placeholderName="<placeholder-name>"
>
  [<Placeholders>
    <Placeholder ... />
    ...
   </Placeholders>]
  [<SectionModelBinding ... >
    ...
   </SectionModelBinding>]
</SectionModelBinding>
```
### Parameters
| Parameter                          | Description | Default | Remark |
|:---                                |:--- |:--- |:--- |
| section[^1]                        | The name of the section as defined in the template. | | |
| modelXPath[^1]                     | The XPath expression which can be applied on the model to get to the XML element to bind to the section. | | |
| placeholderName                    | The name of the placeholder of the current element. | The same as `section` | |

### Child sections
| Section                            | Description |
|:---                                |:--- |
| Placeholder                        | See [Placeholder](#placeholder) |
| SectionModelBinding                | A SectionModelBinding configuration in itself can contain a SectionModelBinding configuration. So it can be defined recursively. |

## Placeholder

### Syntax
``` xml
<Placeholder
  name="<placeholder-name>"
  modelXPath="<model-xpath>"
/>
```

### Parameters

| Parameter                          | Description | Default | Remark |
|:---                                |:--- |:--- |:--- |
| name[^1]                           | The name of the placeholder. | | |
| modelXPath[^1]                     | The XPath expression which can be applied on the current element to get the element for the placeholder. | | |


[comment]: Footnotes
[^1]: required parameter