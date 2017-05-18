""" Top level exceptions for ML from AppScale. """

class MLException(Exception):
  """ Top level exception for search. """
  pass

class InternalError(MLException):
  """ Internal error exception. """
  pass

class NotConfiguredError(MLException):
  """ Search is not configured. """
  pass

