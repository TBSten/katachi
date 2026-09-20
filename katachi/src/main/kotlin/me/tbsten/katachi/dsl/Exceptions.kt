package me.tbsten.katachi.dsl

/**
 * Base of every error raised while `architecture { }` is being evaluated, long before any
 * file is read. Catch this to treat "the definition itself is wrong" as one case.
 *
 * It extends [IllegalArgumentException] because the offending value is always something
 * the caller passed to the DSL.
 */
public open class KatachiDeclarationException internal constructor(
    message: String,
) : IllegalArgumentException(message)

/**
 * A group name or a role name is not a valid identifier.
 *
 * @property name the rejected name, as written.
 * @property declaredAt where it was written.
 */
public class InvalidIdentifierException internal constructor(
    public val name: String,
    public val declaredAt: DeclarationSite,
    message: String,
) : KatachiDeclarationException(message)

/**
 * The same group name was declared twice directly under the same parent, or the same role
 * name twice inside the same group.
 *
 * @property name the duplicated name.
 * @property firstDeclaredAt where the name was declared the first time.
 * @property declaredAt where the rejected second declaration was written.
 */
public class DuplicateDeclarationException internal constructor(
    public val name: String,
    public val firstDeclaredAt: DeclarationSite,
    public val declaredAt: DeclarationSite,
    message: String,
) : KatachiDeclarationException(message)
