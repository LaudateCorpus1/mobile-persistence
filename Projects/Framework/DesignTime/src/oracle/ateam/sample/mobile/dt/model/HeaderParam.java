/*******************************************************************************
 Copyright (c) 2014,2015, Oracle and/or its affiliates. All rights reserved.
 
 $revision_history$
 06-mar-2016   Steven Davelaar
 1.1           Equals method should compare header names case insensitive
 06-feb-2013   Steven Davelaar
 1.0           initial creation
******************************************************************************/
package oracle.ateam.sample.mobile.dt.model;

public class HeaderParam
{
  
  private String name;
  private String value;
  public HeaderParam()
  {
    super();
  }

  public void setName(String key)
  {
    this.name = key;
  }

  public String getName()
  {
    return name;
  }

  public void setValue(String value)
  {
    this.value = value;
  }

  public String getValue()
  {
    return value;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (obj instanceof HeaderParam)
    {
      HeaderParam otherParam = (HeaderParam) obj;
      return otherParam.getName()!=null && otherParam.getName().equalsIgnoreCase(getName());
    }
    return false;
  }
}
