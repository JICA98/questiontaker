package com.questiontaker;

import com.questiontaker.data.QuestionRepository;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<QuestionRepository> repositoryProvider;

  private MainActivity_MembersInjector(Provider<QuestionRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectRepository(instance, repositoryProvider.get());
  }

  public static MembersInjector<MainActivity> create(
      Provider<QuestionRepository> repositoryProvider) {
    return new MainActivity_MembersInjector(repositoryProvider);
  }

  @InjectedFieldSignature("com.questiontaker.MainActivity.repository")
  public static void injectRepository(MainActivity instance, QuestionRepository repository) {
    instance.repository = repository;
  }
}
