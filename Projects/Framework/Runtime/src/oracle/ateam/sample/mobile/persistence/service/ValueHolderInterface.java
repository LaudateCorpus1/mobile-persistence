/*******************************************************************************
 Copyright (c) 2014,2015, Oracle and/or its affiliates. All rights reserved.
 
 $revision_history$
 06-feb-2013   Steven Davelaar
 1.0           initial creation
******************************************************************************/
package oracle.ateam.sample.mobile.persistence.service;

/**
 * Interface used for lazy loading (aka as Indirection) of 1:1 relations in a data object. 
 * 
 * @deprecated Use the class with same name in oracle.ateam.sample.mobile.v2.persistence.* instead
 */
public interface ValueHolderInterface
{
  Object getValue();
  void setValue(Object value);
}
