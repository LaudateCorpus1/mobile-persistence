/*******************************************************************************
 Copyright (c) 2014,2015, Oracle and/or its affiliates. All rights reserved.
 
 $revision_history$
 02-nov-2014   Steven Davelaar
 1.0           initial creation
******************************************************************************/
package oracle.ateam.sample.mobile.persistence.metadata;

import oracle.adfmf.util.Utility;

import oracle.ateam.sample.mobile.util.MessageUtils;

/**
 * Implementation of SOAP method header parameter or REST resource header parameter that can be instantiated programmatically
 * 
 * @deprecated Use the class with same name in oracle.ateam.sample.mobile.v2.persistence.* instead
 */
public class MethodHeaderParameterImpl
  implements MethodHeaderParameter
{
  
  private String name;
  private String value;

  public MethodHeaderParameterImpl(String name)
  {
    this.name= name;
  }

  public void setName(String name)
  {
    this.name = name;
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
}
